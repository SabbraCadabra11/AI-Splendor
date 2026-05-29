package com.aisplendor.engine;

import com.aisplendor.config.DynamicReasoningConfig;
import com.aisplendor.config.GameConfig;
import com.aisplendor.config.StageConfig;
import com.aisplendor.model.*;
import com.aisplendor.model.action.AgentResponse;
import com.aisplendor.model.action.GameAction;
import com.aisplendor.model.action.PurchaseCardAction;
import com.aisplendor.model.action.ReserveCardAction;
import com.aisplendor.model.action.TakeTokensAction;
import com.aisplendor.model.event.*;
import com.aisplendor.service.GameEventLogger;
import com.aisplendor.service.GameLogReader;
import com.aisplendor.service.OpenRouterService;
import com.aisplendor.service.PromptService;
import com.aisplendor.util.GameStateFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class GameSimulator {
    private static final Logger logger = LoggerFactory.getLogger(GameSimulator.class);

    private final GameEngine engine;
    private final OpenRouterService llmService0;
    private final OpenRouterService llmService1;
    private final PromptService promptService;
    private final boolean semiAuto;
    private final boolean debugMode;
    private final StageConfig stageConfig;
    private final int memorySize0;
    private final int memorySize1;

    public GameSimulator(String apiKey, String model0, String model1,
            DynamicReasoningConfig dynamicReasoning0, DynamicReasoningConfig dynamicReasoning1,
            boolean semiAuto, boolean debugMode,
            StageConfig stageConfig, int memorySize0, int memorySize1, String promptCachingSetting) {
        this.engine = new GameEngine();
        this.llmService0 = new OpenRouterService(apiKey, model0, dynamicReasoning0, debugMode, promptCachingSetting);
        this.llmService1 = new OpenRouterService(apiKey, model1, dynamicReasoning1, debugMode, promptCachingSetting);
        this.promptService = new PromptService();
        this.semiAuto = semiAuto;
        this.debugMode = debugMode;
        this.stageConfig = stageConfig;
        this.memorySize0 = memorySize0;
        this.memorySize1 = memorySize1;
    }

    public static void initializeGame(Path propertiesFile) {
        String apiKey = System.getenv("OPENROUTER_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            logger.error("OPENROUTER_API_KEY environment variable is not set.");
            return;
        }

        GameConfig config = (propertiesFile != null)
                ? new GameConfig(propertiesFile)
                : new GameConfig();
        String model0 = config.getPlayer0Model();
        String model1 = config.getPlayer1Model();
        DynamicReasoningConfig dynamicReasoning0 = config.getDynamicReasoningConfig(0);
        DynamicReasoningConfig dynamicReasoning1 = config.getDynamicReasoningConfig(1);
        boolean semiAuto = config.isSemiAuto();
        boolean debugMode = config.isDebugMode();
        StageConfig stageConfig = config.getStageConfig();
        int memorySize0 = config.getPlayerMemorySize(0);
        int memorySize1 = config.getPlayerMemorySize(1);
        String promptCachingSetting = config.getPromptCachingSetting();

        logger.info("Configured Player 0 with: {} (reasoning: {}, memory: {})", model0,
                dynamicReasoning0, memorySize0);
        logger.info("Configured Player 1 with: {} (reasoning: {}, memory: {})", model1,
                dynamicReasoning1, memorySize1);
        logger.info("Semi-Auto Mode: {}", (semiAuto ? "ENABLED" : "DISABLED"));
        logger.info("Debug Mode: {}", (debugMode ? "ENABLED" : "DISABLED"));
        logger.info("Prompt Caching: {}", promptCachingSetting);
        if (stageConfig.hasStage()) {
            logger.info("Stage: {} Leg {}", stageConfig.stage(), stageConfig.leg());
            if (stageConfig.isSecondLeg()) {
                logger.info("First Leg Result: {} - {}", stageConfig.player0FirstLegScore(),
                        stageConfig.player1FirstLegScore());
            }
        }

        // Generate unique game ID based on timestamp (matches logback format)
        String gameId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        GameSimulator simulator = new GameSimulator(apiKey, model0, model1, dynamicReasoning0, dynamicReasoning1,
                semiAuto, debugMode, stageConfig, memorySize0, memorySize1, promptCachingSetting);
        GameState state = setupInitialState();

        simulator.run(state, gameId, model0, model1);
    }

    /**
     * Resumes an interrupted game from a log file.
     * 
     * @param logFile Path to the NDJSON log file to resume from
     */
    public static void resumeGame(Path logFile) {
        String apiKey = System.getenv("OPENROUTER_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            logger.error("OPENROUTER_API_KEY environment variable is not set.");
            return;
        }

        try {
            GameLogReader reader = new GameLogReader();
            GameLogReader.ResumeData resumeData = reader.parseLogForResume(logFile);

            logger.info("--- Resuming Game from {} ---", logFile);
            logger.info("Original Game ID: {}", resumeData.originalGameId());
            logger.info("Player 0 Model: {}", resumeData.player0Model());
            logger.info("Player 1 Model: {}", resumeData.player1Model());
            logger.info("Resuming at Turn {}, Player {}'s turn",
                    resumeData.resumeState().turnNumber(),
                    resumeData.resumeState().currentPlayerIndex());

            // Load config for reasoning settings (models are overridden from log)
            GameConfig config = new GameConfig();
            DynamicReasoningConfig dynamicReasoning0 = config.getDynamicReasoningConfig(0);
            DynamicReasoningConfig dynamicReasoning1 = config.getDynamicReasoningConfig(1);
            boolean semiAuto = config.isSemiAuto();
            boolean debugMode = config.isDebugMode();
            // Note: Stage config is not preserved in resume - use none()
            StageConfig stageConfig = config.getStageConfig();
            int memorySize0 = config.getPlayerMemorySize(0);
            int memorySize1 = config.getPlayerMemorySize(1);
            String promptCachingSetting = config.getPromptCachingSetting();

            // Generate new game ID with resume suffix
            String newGameId = resumeData.originalGameId() + "_resumed_"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));

            GameSimulator simulator = new GameSimulator(
                    apiKey,
                    resumeData.player0Model(),
                    resumeData.player1Model(),
                    dynamicReasoning0,
                    dynamicReasoning1,
                    semiAuto,
                    debugMode,
                    stageConfig,
                    memorySize0,
                    memorySize1,
                    promptCachingSetting);

            simulator.run(resumeData.resumeState(), newGameId,
                    resumeData.player0Model(), resumeData.player1Model(),
                    resumeData.player0AccumulatedTimeMs(), resumeData.player1AccumulatedTimeMs());

        } catch (IOException e) {
            logger.error("Failed to resume game from {}: {}", logFile, e.getMessage());
        }
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

    public void run(GameState initialState, String gameId, String model0, String model1) {
        run(initialState, gameId, model0, model1, 0L, 0L);
    }

    public void run(GameState initialState, String gameId, String model0, String model1,
            long initialP0TimeMs, long initialP1TimeMs) {
        GameState state = initialState;
        logger.info("--- Game Started ---");
        long player0TotalTimeMs = initialP0TimeMs;
        long player1TotalTimeMs = initialP1TimeMs;

        try (GameEventLogger eventLogger = new GameEventLogger(gameId)) {
            // Log game start event
            eventLogger.log(new GameStartedEvent(
                    Instant.now(), gameId, model0, model1, state));

            while (!state.isGameOver()) {
                Player currentPlayer = state.players().get(state.currentPlayerIndex());
                logger.info(GameStateFormatter.format(state, List.of(model0, model1)));
                logger.info("Turn {} - Player {}'s move", state.turnNumber(), currentPlayer.id());
                logger.info("Points: {}, Budget: {}", currentPlayer.score(),
                        GameStateFormatter.formatBudget(currentPlayer));

                // Log turn start event with full state snapshot
                eventLogger.log(new TurnStartedEvent(
                        Instant.now(), state.turnNumber(), state.currentPlayerIndex(), state));

                if (debugMode) {
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper debugMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        logger.info("[DEBUG] Game State JSON:\n{}",
                                debugMapper.writerWithDefaultPrettyPrinter().writeValueAsString(state));
                    } catch (Exception e) {
                        logger.warn("Failed to serialize game state for debug: {}", e.getMessage());
                    }
                }

                if (semiAuto) {
                    logger.info("Press Enter to trigger Player {}'s move...", currentPlayer.id());
                    try {
                        System.in.read();
                    } catch (IOException e) {
                        logger.error("Error reading from stdin", e);
                    }
                }

                long moveStartMs = System.currentTimeMillis();

                String systemPrompt = promptService.getSystemPrompt(currentPlayer.reasoningHistory(), stageConfig,
                        state.currentPlayerIndex());
                OpenRouterService currentLlm = (state.currentPlayerIndex() == 0) ? llmService0 : llmService1;

                final int MAX_LOGIC_RETRIES = 3;
                final long MAX_NETWORK_WAIT_MS = 10 * 60 * 1000; // 10 minutes for network issues
                final long INITIAL_BACKOFF_MS = 1000;

                AgentResponse response = null;
                String lastError = null;
                String lastResponse = null; // Track previous response for retry feedback
                boolean validAction = false;
                long moveDurationMs = 0L;

                for (int attempt = 0; attempt < MAX_LOGIC_RETRIES && !validAction; attempt++) {
                    long networkWaitStart = System.currentTimeMillis();
                    long backoff = INITIAL_BACKOFF_MS;

                    while (!validAction) { // Network retry loop
                        try {
                            String retryContext = null;
                            if (attempt > 0 && backoff == INITIAL_BACKOFF_MS) {
                                // Only log retry on first network attempt of a new logic retry
                                logger.warn("Retry attempt {} for Player {}. Error: {}", attempt, currentPlayer.id(),
                                        lastError);
                                // Log retry event
                                eventLogger.log(new RetryEvent(
                                        Instant.now(), currentPlayer.id(), attempt, lastError));
                                retryContext = promptService.getRetryPrompt(lastError, lastResponse);
                            } else if (backoff > INITIAL_BACKOFF_MS && attempt > 0) {
                                // Network retry on a logic retry - keep the retry context
                                retryContext = promptService.getRetryPrompt(lastError, lastResponse);
                            }
                            response = currentLlm.getNextMove(state, systemPrompt, retryContext);

                            logger.info("Reasoning: {}", response.reasoning());
                            logger.info("Action: {}", response.action());

                            // Log reasoning event
                            eventLogger.log(new ReasoningEvent(
                                    Instant.now(), currentPlayer.id(), response.reasoning()));

                            engine.validateAction(state, response.action());
                            validAction = true;
                            moveDurationMs = System.currentTimeMillis() - moveStartMs;
                            if (state.currentPlayerIndex() == 0) {
                                player0TotalTimeMs += moveDurationMs;
                            } else {
                                player1TotalTimeMs += moveDurationMs;
                            }

                            // Log successful action event
                            eventLogger.log(new ActionEvent(
                                    Instant.now(), currentPlayer.id(), response.action(), true, moveDurationMs));
                        } catch (IllegalArgumentException e) {
                            lastError = "Invalid action: " + e.getMessage();
                            // Capture the failed response for retry feedback
                            if (response != null) {
                                lastResponse = "Reasoning: " + response.reasoning() + "\nAction: " + response.action();
                            }
                            break; // Logic error - exit to outer loop for retry
                        } catch (com.fasterxml.jackson.core.JsonParseException e) {
                            lastError = "Malformed JSON response: " + e.getOriginalMessage();
                            lastResponse = null; // Can't capture response if JSON was malformed
                            break; // Logic error - exit to outer loop for retry
                        } catch (java.net.SocketTimeoutException | java.net.ConnectException e) {
                            // Transient network errors - retry with backoff
                            long elapsed = System.currentTimeMillis() - networkWaitStart;
                            if (elapsed > MAX_NETWORK_WAIT_MS) {
                                lastError = "Network timeout after " + (elapsed / 1000) + "s: " + e.getMessage();
                                break; // Give up, count as logic retry
                            }
                            logger.warn("Network error, retrying in {}ms... ({})", backoff, e.getMessage());
                            try {
                                Thread.sleep(backoff);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                lastError = "Interrupted during network retry";
                                break;
                            }
                            backoff = Math.min(backoff * 2, 30_000); // Cap at 30s
                            // Stay in network loop
                        } catch (Exception e) {
                            // Unknown error - check if it looks like a network issue
                            String msg = e.getMessage();
                            if (msg != null && (msg.contains("Connection") || msg.contains("timeout") ||
                                    msg.contains("UnknownHost") || msg.contains("Network"))) {
                                long elapsed = System.currentTimeMillis() - networkWaitStart;
                                if (elapsed > MAX_NETWORK_WAIT_MS) {
                                    lastError = "Network timeout after " + (elapsed / 1000) + "s: " + msg;
                                    break;
                                }
                                logger.warn("Possible network error, retrying in {}ms... ({})", backoff, msg);
                                try {
                                    Thread.sleep(backoff);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    lastError = "Interrupted during network retry";
                                    break;
                                }
                                backoff = Math.min(backoff * 2, 30_000);
                            } else {
                                lastError = "Unexpected error: " + msg;
                                break; // Unknown error - treat as logic error
                            }
                        }
                    }
                }

                if (!validAction) {
                    moveDurationMs = System.currentTimeMillis() - moveStartMs;
                    if (state.currentPlayerIndex() == 0) {
                        player0TotalTimeMs += moveDurationMs;
                    } else {
                        player1TotalTimeMs += moveDurationMs;
                    }
                    logger.error(
                            "Player {} failed to provide a valid action after {} retries. Last error: {}. Skipping turn. Time taken: {}",
                            currentPlayer.id(), MAX_LOGIC_RETRIES, lastError, formatDuration(moveDurationMs));
                    // Skip turn by manually advancing the game state
                    int nextPlayerIndex = (state.currentPlayerIndex() + 1) % state.players().size();
                    int nextTurn = (nextPlayerIndex == 0) ? state.turnNumber() + 1 : state.turnNumber();
                    state = new GameState(state.board(), state.players(), nextPlayerIndex, nextTurn, state.isGameOver(),
                            state.winnerReason());
                    continue;
                }

                // Update Player Memory — compress reasoning to a one-line summary
                int memorySize = (state.currentPlayerIndex() == 0) ? memorySize0 : memorySize1;
                List<String> newHistory = new ArrayList<>(currentPlayer.reasoningHistory());
                String compressedReasoning = compressReasoning(
                        state.turnNumber(), response.reasoning(), response.action());
                newHistory.add(compressedReasoning);
                if (newHistory.size() > memorySize) {
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
                logger.info("[TIME] Player {}'s move took {}", currentPlayer.id(), formatDuration(moveDurationMs));
                logger.debug("Applied action. New state summary: P0:{}, P1:{}",
                        state.players().get(0).score(), state.players().get(1).score());
            }

            logger.info(GameStateFormatter.format(state, List.of(model0, model1)));
            logger.info("--- Game Over ---");
            logger.info("Winner: {}", state.winnerReason());

            // Build final scores map
            Map<Integer, Integer> finalScores = new HashMap<>();
            for (Player p : state.players()) {
                logger.info("Player {}: {} points", p.id(), p.score());
                finalScores.put(p.id(), p.score());
            }

            logger.info("--- Execution Time Summary ---");
            logger.info("Player 0 ({}) total time: {}", model0, formatDuration(player0TotalTimeMs));
            logger.info("Player 1 ({}) total time: {}", model1, formatDuration(player1TotalTimeMs));

            // Determine winner index (null if tie or no clear winner)
            Integer winnerIndex = state.players().stream()
                    .max(Comparator.comparingInt(Player::score))
                    .map(Player::id)
                    .orElse(null);

            // Log game ended event
            eventLogger.log(new GameEndedEvent(
                    Instant.now(), winnerIndex, state.winnerReason(), finalScores));

        } catch (Exception e) {
            logger.error("An error occurred during the game simulation:", e);
        }
    }

    /**
     * Compresses a full reasoning string into a structured one-liner for the memory window.
     * Format: "T{turn}: {action_summary} — {rationale extracted from reasoning}"
     *
     * @param turnNumber The current turn number
     * @param reasoning  The full reasoning string from the LLM
     * @param action     The action that was taken
     * @return A compressed one-line summary
     */
    private static String compressReasoning(int turnNumber, String reasoning, GameAction action) {
        String actionSummary = summarizeAction(action);

        // Extract the core rationale — take the first sentence or the last sentence
        // as LLMs tend to put conclusions at the start or end
        String rationale = extractRationale(reasoning);

        return String.format("T%d: %s — %s", turnNumber, actionSummary, rationale);
    }

    private static String summarizeAction(GameAction action) {
        return switch (action) {
            case TakeTokensAction take -> {
                if (take.tokens() == null || take.tokens().isEmpty()) {
                    yield "Took tokens (empty)";
                }
                String colors = take.tokens().entrySet().stream()
                        .filter(e -> e.getValue() > 0)
                        .map(e -> formatColorShort(e.getKey()) + (e.getValue() > 1 ? "x" + e.getValue() : ""))
                        .collect(java.util.stream.Collectors.joining("+"));
                yield "Took " + colors;
            }
            case PurchaseCardAction purchase -> "Purchased " + purchase.cardId();
            case ReserveCardAction reserve -> {
                if (reserve.cardId() != null) {
                    yield "Reserved " + reserve.cardId();
                } else if (reserve.deckLevel() != null) {
                    yield "Reserved blind from " + reserve.deckLevel();
                }
                yield "Reserved card";
            }
        };
    }

    private static String extractRationale(String reasoning) {
        if (reasoning == null || reasoning.isBlank()) {
            return "No reasoning provided";
        }

        // Clean up and take the first meaningful sentence
        String cleaned = reasoning.strip();

        // Try to find the first sentence
        int end = -1;
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (c == '.' || c == '!' || c == '?') {
                // Make sure it's not a decimal point (e.g., "4.5")
                if (c == '.' && i > 0 && i < cleaned.length() - 1
                        && Character.isDigit(cleaned.charAt(i - 1))
                        && Character.isDigit(cleaned.charAt(i + 1))) {
                    continue;
                }
                end = i + 1;
                break;
            }
        }

        if (end > 0 && end <= cleaned.length()) {
            return cleaned.substring(0, end).strip();
        }

        // No sentence boundary found — take the whole thing
        return cleaned;
    }

    private static String formatColorShort(Color color) {
        return switch (color) {
            case WHITE -> "WHT";
            case BLUE -> "BLU";
            case GREEN -> "GRN";
            case RED -> "RED";
            case BLACK -> "BLK";
            case GOLD -> "GLD";
        };
    }

    private static String formatDuration(long durationMs) {
        long minutes = (durationMs / 1000) / 60;
        long seconds = (durationMs / 1000) % 60;
        long millis = durationMs % 1000;
        return String.format("%02d:%02d.%03d", minutes, seconds, millis);
    }
}
