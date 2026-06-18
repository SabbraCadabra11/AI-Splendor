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
import com.aisplendor.service.GameEventPublisher;
import com.aisplendor.service.GameLogReader;
import com.aisplendor.service.OpenRouterService;
import com.aisplendor.exception.ApiException;
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
    private final String player0ModelId;
    private final String player1ModelId;
    private final PromptService promptService;
    private final boolean semiAuto;
    private final boolean debugMode;
    private final StageConfig stageConfig;
    private final int memorySize0;
    private final int memorySize1;
    private final double player0InputCost;
    private final double player0OutputCost;
    private final double player1InputCost;
    private final double player1OutputCost;
    private final GameEventPublisher publisher;

    public GameSimulator(String apiKey, String model0, String model1,
            DynamicReasoningConfig dynamicReasoning0, DynamicReasoningConfig dynamicReasoning1,
            boolean semiAuto, boolean debugMode,
            StageConfig stageConfig, int memorySize0, int memorySize1, String promptCachingSetting,
            double player0InputCost, double player0OutputCost,
            double player1InputCost, double player1OutputCost) {
        this(apiKey, model0, model1, dynamicReasoning0, dynamicReasoning1, semiAuto, debugMode, stageConfig, memorySize0, memorySize1, promptCachingSetting, player0InputCost, player0OutputCost, player1InputCost, player1OutputCost, null);
    }

    public GameSimulator(String apiKey, String model0, String model1,
            DynamicReasoningConfig dynamicReasoning0, DynamicReasoningConfig dynamicReasoning1,
            boolean semiAuto, boolean debugMode,
            StageConfig stageConfig, int memorySize0, int memorySize1, String promptCachingSetting,
            double player0InputCost, double player0OutputCost,
            double player1InputCost, double player1OutputCost,
            GameEventPublisher publisher) {
        this.engine = new GameEngine();
        this.player0ModelId = model0;
        this.player1ModelId = model1;
        this.player0InputCost = player0InputCost;
        this.player0OutputCost = player0OutputCost;
        this.player1InputCost = player1InputCost;
        this.player1OutputCost = player1OutputCost;
        this.llmService0 = new OpenRouterService(apiKey, model0, dynamicReasoning0, debugMode, promptCachingSetting, player0InputCost, player0OutputCost);
        this.llmService1 = new OpenRouterService(apiKey, model1, dynamicReasoning1, debugMode, promptCachingSetting, player1InputCost, player1OutputCost);
        this.promptService = new PromptService();
        this.semiAuto = semiAuto;
        this.debugMode = debugMode;
        this.stageConfig = stageConfig;
        this.memorySize0 = memorySize0;
        this.memorySize1 = memorySize1;
        this.publisher = publisher;
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

        double player0InputCost = config.getPlayerInputTokenCost(0);
        double player0OutputCost = config.getPlayerOutputTokenCost(0);
        double player1InputCost = config.getPlayerInputTokenCost(1);
        double player1OutputCost = config.getPlayerOutputTokenCost(1);

        logger.info("Configured Player 0 with: {} (reasoning: {}, memory: {}, inputCost=${}/1M, outputCost=${}/1M)", model0,
                dynamicReasoning0, memorySize0, player0InputCost, player0OutputCost);
        logger.info("Configured Player 1 with: {} (reasoning: {}, memory: {}, inputCost=${}/1M, outputCost=${}/1M)", model1,
                dynamicReasoning1, memorySize1, player1InputCost, player1OutputCost);
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

        // Generate unique game ID based on model slugs, reasoning levels, and timestamp
        String slug0 = com.aisplendor.util.GameStateFormatter.getModelSlug(model0);
        String slug1 = com.aisplendor.util.GameStateFormatter.getModelSlug(model1);
        String r0 = com.aisplendor.util.GameStateFormatter.getReasoningLevelSuffix(dynamicReasoning0);
        String r1 = com.aisplendor.util.GameStateFormatter.getReasoningLevelSuffix(dynamicReasoning1);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd_HHmmss"));
        String gameId = slug0 + "-" + r0 + "_" + slug1 + "-" + r1 + "-" + timestamp;

        GameSimulator simulator = new GameSimulator(apiKey, model0, model1, dynamicReasoning0, dynamicReasoning1,
                semiAuto, debugMode, stageConfig, memorySize0, memorySize1, promptCachingSetting,
                player0InputCost, player0OutputCost, player1InputCost, player1OutputCost);
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

            // Generate new game ID with model slugs, reasoning levels, timestamp, and resume suffix
            String slug0 = com.aisplendor.util.GameStateFormatter.getModelSlug(resumeData.player0Model());
            String slug1 = com.aisplendor.util.GameStateFormatter.getModelSlug(resumeData.player1Model());
            String r0 = com.aisplendor.util.GameStateFormatter.getReasoningLevelSuffix(dynamicReasoning0);
            String r1 = com.aisplendor.util.GameStateFormatter.getReasoningLevelSuffix(dynamicReasoning1);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd_HHmmss"));
            String newGameId = slug0 + "-" + r0 + "_" + slug1 + "-" + r1 + "-" + timestamp + "_resumed";

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
                    promptCachingSetting,
                    resumeData.player0InputCost(),
                    resumeData.player0OutputCost(),
                    resumeData.player1InputCost(),
                    resumeData.player1OutputCost());

            simulator.run(resumeData.resumeState(), newGameId,
                    resumeData.player0Model(), resumeData.player1Model(),
                    resumeData.player0AccumulatedTimeMs(), resumeData.player1AccumulatedTimeMs(),
                    resumeData.player0AccumulatedTokens(), resumeData.player1AccumulatedTokens());

        } catch (IOException e) {
            logger.error("Failed to resume game from {}: {}", logFile, e.getMessage());
        }
    }

    public static GameState setupInitialState() {
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
        run(initialState, gameId, model0, model1, 0L, 0L, TokenUsage.zero(), TokenUsage.zero());
    }

    public void run(GameState initialState, String gameId, String model0, String model1,
            long initialP0TimeMs, long initialP1TimeMs) {
        run(initialState, gameId, model0, model1, initialP0TimeMs, initialP1TimeMs, TokenUsage.zero(), TokenUsage.zero());
    }

    public void run(GameState initialState, String gameId, String model0, String model1,
            long initialP0TimeMs, long initialP1TimeMs, TokenUsage initialP0Tokens, TokenUsage initialP1Tokens) {
        redirectLogbackFileAppender(gameId + ".log");
        GameState state = initialState;
        logger.info("--- Game Started ---");
        long player0TotalTimeMs = initialP0TimeMs;
        long player1TotalTimeMs = initialP1TimeMs;
        TokenUsage player0Tokens = initialP0Tokens != null ? initialP0Tokens : TokenUsage.zero();
        TokenUsage player1Tokens = initialP1Tokens != null ? initialP1Tokens : TokenUsage.zero();
        boolean aborted = false;

        try (GameEventLogger eventLogger = new GameEventLogger(gameId, this.publisher)) {
            // Log game start event
            eventLogger.log(new GameStartedEvent(
                    Instant.now(), gameId, this.player0ModelId, this.player1ModelId, model0, model1,
                    player0InputCost, player0OutputCost, player1InputCost, player1OutputCost, state));

            while (!state.isGameOver()) {
                if (Thread.currentThread().isInterrupted()) {
                    aborted = true;
                    break;
                }
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
                final int MAX_API_RETRIES = 20;
                final long INITIAL_BACKOFF_MS = 1000;

                AgentResponse response = null;
                String lastError = null;
                String lastResponse = null; // Track previous response for retry feedback
                boolean validAction = false;
                long moveDurationMs = 0L;
                int apiRetries = 0;

                for (int attempt = 0; attempt < MAX_LOGIC_RETRIES && !validAction; attempt++) {
                    if (Thread.currentThread().isInterrupted()) {
                        aborted = true;
                        break;
                    }
                    long networkWaitStart = System.currentTimeMillis();
                    long backoff = INITIAL_BACKOFF_MS;

                    while (!validAction) { // Network retry loop
                        if (Thread.currentThread().isInterrupted()) {
                            aborted = true;
                            break;
                        }
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
                            logger.info("Token Usage: prompt={}, completion={}, cost=${}",
                                    response.tokenUsage().promptTokens(),
                                    response.tokenUsage().completionTokens(),
                                    String.format("%.6f", response.tokenUsage().cost()));

                            // Log reasoning event
                            eventLogger.log(new ReasoningEvent(
                                    Instant.now(), currentPlayer.id(), response.reasoning(), response.tokenUsage()));

                            engine.validateAction(state, response.action());
                            validAction = true;
                            moveDurationMs = System.currentTimeMillis() - moveStartMs;
                            if (state.currentPlayerIndex() == 0) {
                                player0TotalTimeMs += moveDurationMs;
                                player0Tokens = player0Tokens.add(response.tokenUsage());
                            } else {
                                player1TotalTimeMs += moveDurationMs;
                                player1Tokens = player1Tokens.add(response.tokenUsage());
                            }

                            // Log successful action event
                            eventLogger.log(new ActionEvent(
                                    Instant.now(), currentPlayer.id(), response.action(), true, moveDurationMs));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            lastError = "Interrupted: " + e.getMessage();
                            aborted = true;
                            break;
                        } catch (IllegalArgumentException e) {
                            lastError = "Invalid action: " + e.getMessage();
                            // Capture the failed response for retry feedback
                            if (response != null) {
                                lastResponse = "Reasoning: " + response.reasoning() + "\nAction: " + response.action();
                            }
                            break; // Logic error - exit to outer loop for retry
                        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                            lastError = "Malformed JSON response: " + e.getOriginalMessage();
                            lastResponse = null; // Can't capture response if JSON was malformed
                            break; // Logic error - exit to outer loop for retry
                        } catch (ApiException e) {
                            apiRetries++;
                            if (apiRetries >= MAX_API_RETRIES) {
                                lastError = "API error limit reached (" + MAX_API_RETRIES + "): " + e.getMessage();
                                aborted = true;
                                break;
                            }
                            logger.warn("API error, retrying {}/{} in {}ms... ({})", apiRetries, MAX_API_RETRIES, backoff, e.getMessage());
                            try {
                                Thread.sleep(backoff);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                lastError = "Interrupted during API retry";
                                aborted = true;
                                break;
                            }
                            backoff = Math.min(backoff * 2, 30_000);
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

                if (aborted) {
                    break;
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

                // Update Player Memory — store full reasoning with turn and action context
                int memorySize = (state.currentPlayerIndex() == 0) ? memorySize0 : memorySize1;
                List<String> newHistory = new ArrayList<>(currentPlayer.reasoningHistory());
                String actionSummary = summarizeAction(response.action());
                String reasoningText = (response.reasoning() == null || response.reasoning().isBlank())
                        ? "No reasoning provided"
                        : response.reasoning().strip();
                String fullReasoningEntry = String.format("T%d (%s):\n%s",
                        state.turnNumber(), actionSummary, reasoningText);
                newHistory.add(fullReasoningEntry);
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

            if (aborted) {
                logger.info("--- Game Aborted ---");

                Map<Integer, Integer> finalScores = new HashMap<>();
                for (Player p : state.players()) {
                    finalScores.put(p.id(), p.score());
                }

                Map<Integer, TokenUsage> playerUsages = new HashMap<>();
                playerUsages.put(0, player0Tokens);
                playerUsages.put(1, player1Tokens);

                eventLogger.log(new GameEndedEvent(
                        Instant.now(), null, "Aborted", finalScores, playerUsages));
            } else {
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

                logger.info("--- Token Usage & Cost Summary ---");
                logger.info("Player 0 ({}) - Prompt Tokens: {}, Completion Tokens: {}, Estimated Cost: ${}",
                        model0, player0Tokens.promptTokens(), player0Tokens.completionTokens(), String.format("%.6f", player0Tokens.cost()));
                logger.info("Player 1 ({}) - Prompt Tokens: {}, Completion Tokens: {}, Estimated Cost: ${}",
                        model1, player1Tokens.promptTokens(), player1Tokens.completionTokens(), String.format("%.6f", player1Tokens.cost()));

                // Determine winner index (null if tie or no clear winner)
                Integer winnerIndex = state.players().stream()
                        .max(Comparator.comparingInt(Player::score))
                        .map(Player::id)
                        .orElse(null);

                Map<Integer, TokenUsage> playerUsages = new HashMap<>();
                playerUsages.put(0, player0Tokens);
                playerUsages.put(1, player1Tokens);

                // Log game ended event
                eventLogger.log(new GameEndedEvent(
                        Instant.now(), winnerIndex, state.winnerReason(), finalScores, playerUsages));
            }

        } catch (Exception e) {
            logger.error("An error occurred during the game simulation:", e);
        }
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

    private void redirectLogbackFileAppender(String filename) {
        try {
            org.slf4j.ILoggerFactory factory = org.slf4j.LoggerFactory.getILoggerFactory();
            if (factory instanceof ch.qos.logback.classic.LoggerContext context) {
                ch.qos.logback.classic.Logger rootLogger = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
                ch.qos.logback.core.Appender<ch.qos.logback.classic.spi.ILoggingEvent> appender = rootLogger.getAppender("FILE");
                if (appender instanceof ch.qos.logback.core.FileAppender<ch.qos.logback.classic.spi.ILoggingEvent> fileAppender) {
                    fileAppender.stop();
                    String currentFile = fileAppender.getFile();
                    if (currentFile != null) {
                        java.nio.file.Path oldPath = java.nio.file.Path.of(currentFile);
                        java.nio.file.Path newPath = oldPath.getParent() != null ? oldPath.getParent().resolve(filename) : java.nio.file.Path.of(filename);
                        fileAppender.setFile(newPath.toString());
                    } else {
                        fileAppender.setFile(java.nio.file.Path.of("logs").resolve(filename).toString());
                    }
                    fileAppender.start();
                    logger.info("Redirected logback output to: {}", fileAppender.getFile());
                }
            }
        } catch (Throwable t) {
            System.err.println("Could not redirect logback file appender: " + t.getMessage());
        }
    }
}
