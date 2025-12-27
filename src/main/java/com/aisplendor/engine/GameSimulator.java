package com.aisplendor.engine;

import com.aisplendor.config.GameConfig;
import com.aisplendor.config.ReasoningConfig;
import com.aisplendor.model.*;
import com.aisplendor.model.action.AgentResponse;
import com.aisplendor.service.OpenRouterService;
import com.aisplendor.service.PromptService;
import com.aisplendor.util.GameStateFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

public class GameSimulator {
    private static final Logger logger = LoggerFactory.getLogger(GameSimulator.class);

    private final GameEngine engine;
    private final OpenRouterService llmService0;
    private final OpenRouterService llmService1;
    private final PromptService promptService;
    private final boolean semiAuto;

    public GameSimulator(String apiKey, String model0, String model1,
            ReasoningConfig reasoning0, ReasoningConfig reasoning1, boolean semiAuto) {
        this.engine = new GameEngine();
        this.llmService0 = new OpenRouterService(apiKey, model0, reasoning0);
        this.llmService1 = new OpenRouterService(apiKey, model1, reasoning1);
        this.promptService = new PromptService();
        this.semiAuto = semiAuto;
    }

    public static void initializeGame() {
        String apiKey = System.getenv("OPENROUTER_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            logger.error("OPENROUTER_API_KEY environment variable is not set.");
            return;
        }

        GameConfig config = new GameConfig();
        String model0 = config.getPlayer0Model();
        String model1 = config.getPlayer1Model();
        ReasoningConfig reasoning0 = config.getPlayerReasoning(0);
        ReasoningConfig reasoning1 = config.getPlayerReasoning(1);
        boolean semiAuto = config.isSemiAuto();

        logger.info("Configured Player 0 with: {} (reasoning: {})", model0,
                reasoning0.enabled() ? reasoning0.effort() : "disabled");
        logger.info("Configured Player 1 with: {} (reasoning: {})", model1,
                reasoning1.enabled() ? reasoning1.effort() : "disabled");
        logger.info("Semi-Auto Mode: {}", (semiAuto ? "ENABLED" : "DISABLED"));

        GameSimulator simulator = new GameSimulator(apiKey, model0, model1, reasoning0, reasoning1, semiAuto);
        GameState state = setupInitialState();

        simulator.run(state);
    }

    private static GameState setupInitialState() {
        // 2-player setup
        Map<Color, Integer> tokenCounts = new EnumMap<>(Color.class);
        for (Color c : Color.values()) {
            if (c == Color.GOLD)
                tokenCounts.put(c, 5);
            else
                tokenCounts.put(c, 4);
        }
        TokenBank bank = new TokenBank(tokenCounts);

        Map<CardLevel, Queue<DevelopmentCard>> decks = DeckFactory.createStandardDecks();
        Map<CardLevel, List<DevelopmentCard>> faceUp = new EnumMap<>(CardLevel.class);

        for (CardLevel level : CardLevel.values()) {
            List<DevelopmentCard> row = new ArrayList<>();
            Queue<DevelopmentCard> deck = decks.get(level);
            for (int i = 0; i < 4 && !deck.isEmpty(); i++) {
                row.add(deck.poll());
            }
            faceUp.put(level, row);
        }

        List<NobleTile> nobles = new ArrayList<>(DeckFactory.createStandardNobles());
        Collections.shuffle(nobles);
        List<NobleTile> availableNobles = nobles.subList(0, 3);

        Board board = new Board(bank, faceUp, decks, availableNobles);

        Player p1 = new Player(0, new TokenBank(Collections.emptyMap()), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), 0, new HashMap<>(), new ArrayList<>());
        Player p2 = new Player(1, new TokenBank(Collections.emptyMap()), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), 0, new HashMap<>(), new ArrayList<>());

        return new GameState(board, List.of(p1, p2), 0, 1, false, null);
    }

    public void run(GameState initialState) {
        GameState state = initialState;
        logger.info("--- Game Started ---");

        try {
            while (!state.isGameOver()) {
                Player currentPlayer = state.players().get(state.currentPlayerIndex());
                logger.info(GameStateFormatter.format(state));
                logger.info("Turn {} - Player {}'s move", state.turnNumber(), currentPlayer.id());
                logger.info("Points: {}, Tokens: {}", currentPlayer.score(), currentPlayer.tokens().counts());

                if (semiAuto) {
                    logger.info("Press Enter to trigger Player {}'s move...", currentPlayer.id());
                    try {
                        System.in.read();
                    } catch (IOException e) {
                        logger.error("Error reading from stdin", e);
                    }
                }

                String systemPrompt = promptService.getSystemPrompt(currentPlayer.reasoningHistory());
                OpenRouterService currentLlm = (state.currentPlayerIndex() == 0) ? llmService0 : llmService1;

                final int MAX_RETRIES = 3;
                AgentResponse response = null;
                String lastError = null;
                boolean validAction = false;

                for (int attempt = 0; attempt < MAX_RETRIES && !validAction; attempt++) {
                    try {
                        if (attempt > 0) {
                            logger.warn("Retry attempt {} for Player {}. Error: {}", attempt, currentPlayer.id(),
                                    lastError);
                            String retryPrompt = promptService.getRetryPrompt(lastError);
                            response = currentLlm.getNextMove(state, systemPrompt + "\n\n" + retryPrompt);
                        } else {
                            response = currentLlm.getNextMove(state, systemPrompt);
                        }

                        logger.info("Reasoning: {}", response.reasoning());
                        logger.info("Action: {}", response.action());

                        engine.validateAction(state, response.action());
                        validAction = true;
                    } catch (IllegalArgumentException e) {
                        lastError = "Invalid action: " + e.getMessage();
                    } catch (com.fasterxml.jackson.core.JsonParseException e) {
                        lastError = "Malformed JSON response: " + e.getOriginalMessage();
                    } catch (Exception e) {
                        lastError = "API/Parsing error: " + e.getMessage();
                    }
                }

                if (!validAction) {
                    logger.error(
                            "Player {} failed to provide a valid action after {} retries. Last error: {}. Skipping turn.",
                            currentPlayer.id(), MAX_RETRIES, lastError);
                    // Skip turn by manually advancing the game state
                    int nextPlayerIndex = (state.currentPlayerIndex() + 1) % state.players().size();
                    int nextTurn = (nextPlayerIndex == 0) ? state.turnNumber() + 1 : state.turnNumber();
                    state = new GameState(state.board(), state.players(), nextPlayerIndex, nextTurn, state.isGameOver(),
                            state.winnerReason());
                    continue;
                }

                // Update Player Memory
                List<String> newHistory = new ArrayList<>(currentPlayer.reasoningHistory());
                newHistory.add(response.reasoning());
                if (newHistory.size() > 5) {
                    newHistory.remove(0);
                }
                Player updatedCurrentPlayer = new Player(
                        currentPlayer.id(), currentPlayer.tokens(), currentPlayer.purchasedCards(),
                        currentPlayer.reservedCards(), currentPlayer.visitedNobles(), currentPlayer.score(),
                        currentPlayer.bonuses(), newHistory);

                List<Player> updatedPlayers = new ArrayList<>(state.players());
                updatedPlayers.set(state.currentPlayerIndex(), updatedCurrentPlayer);
                state = new GameState(state.board(), updatedPlayers, state.currentPlayerIndex(),
                        state.turnNumber(), state.isGameOver(), state.winnerReason());

                state = engine.applyAction(state, response.action());
                logger.debug("Applied action. New state summary: P0:{}, P1:{}",
                        state.players().get(0).score(), state.players().get(1).score());
            }

            logger.info(GameStateFormatter.format(state));
            logger.info("--- Game Over ---");
            logger.info("Winner: {}", state.winnerReason());
            for (Player p : state.players()) {
                logger.info("Player {}: {} points", p.id(), p.score());
            }

        } catch (Exception e) {
            logger.error("An error occurred during the game simulation:", e);
        }
    }
}
