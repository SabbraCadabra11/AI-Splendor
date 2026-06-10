package com.aisplendor.service;

import com.aisplendor.model.GameState;
import com.aisplendor.model.TokenUsage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Parses NDJSON game logs to extract data needed for resuming interrupted
 * games.
 */
public class GameLogReader {
    private static final Logger logger = LoggerFactory.getLogger(GameLogReader.class);

    private final ObjectMapper objectMapper;

    /**
     * Data extracted from a game log for resuming.
     */
    public record ResumeData(
            String originalGameId,
            String player0Model,
            String player1Model,
            String player0Name,
            String player1Name,
            GameState resumeState,
            long player0AccumulatedTimeMs,
            long player1AccumulatedTimeMs,
            double player0InputCost,
            double player0OutputCost,
            double player1InputCost,
            double player1OutputCost,
            TokenUsage player0AccumulatedTokens,
            TokenUsage player1AccumulatedTokens) {
    }

    /**
     * Inteligentnie mapuje nazwy wyświetlane na techniczne identyfikatory modeli OpenRouter.
     * Zapewnia to wsteczną kompatybilność ze starymi plikami logów.
     */
    public static String resolveModelId(String modelOrDisplayName) {
        if (modelOrDisplayName == null) {
            return null;
        }
        String name = modelOrDisplayName.trim();
        // Usuwamy ewentualne końcówki " (P0)" lub " (P1)"
        if (name.endsWith(" (P0)") || name.endsWith(" (P1)")) {
            name = name.substring(0, name.length() - 5).trim();
        }
        
        // Jeśli zawiera już "/" (np. "google/gemini-3.5-flash"), jest to poprawny techniczny ID
        if (name.contains("/")) {
            return name;
        }
        
        String lower = name.toLowerCase();
        
        if (lower.contains("gemma") && lower.contains("31b")) {
            return "google/gemma-4-31b-it";
        }
        if (lower.contains("gemma") && lower.contains("26b")) {
            return "google/gemma-4-26b-a4b-it";
        }
        if (lower.contains("mistral") && lower.contains("medium")) {
            return "mistralai/mistral-medium-3-5";
        }
        if (lower.contains("gemini") && lower.contains("3.5") && lower.contains("flash")) {
            return "google/gemini-3.5-flash";
        }
        if (lower.contains("gemini") && lower.contains("3") && lower.contains("flash")) {
            return "google/gemini-3-flash-preview";
        }
        if (lower.contains("claude") && lower.contains("haiku")) {
            return "anthropic/claude-haiku-4.5";
        }
        if (lower.contains("gpt") && lower.contains("4o") && lower.contains("mini")) {
            return "openai/gpt-4o-mini";
        }
        if (lower.contains("llama") && lower.contains("70b")) {
            return "meta-llama/llama-3-70b-instruct";
        }
        
        // Szybkie domyślne mapowania na wypadek innych odmian
        if (lower.contains("gemini")) {
            return "google/gemini-3.5-flash";
        }
        if (lower.contains("claude")) {
            return "anthropic/claude-haiku-4.5";
        }
        if (lower.contains("gpt-4")) {
            return "openai/gpt-4o-mini";
        }
        
        return name; // Zwracamy jako fallback
    }

    public GameLogReader() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Parses a game log file and extracts the data needed to resume the game.
     * 
     * Resume logic:
     * 1. Extract model names from GameStartedEvent
     * 2. Find the last ActionEvent with success=true
     * 3. Use the TurnStartedEvent that follows it (state after the action was
     * applied)
     * 4. If no TurnStartedEvent follows, fall back to the last available
     * TurnStartedEvent
     * 
     * @param logFile Path to the NDJSON log file
     * @return ResumeData containing models and the state to resume from
     * @throws IOException if the file cannot be read or parsed
     */
    public ResumeData parseLogForResume(Path logFile) throws IOException {
        String originalGameId = null;
        String player0Model = null;
        String player1Model = null;
        String player0Name = null;
        String player1Name = null;

        double player0InputCost = 0.0;
        double player0OutputCost = 0.0;
        double player1InputCost = 0.0;
        double player1OutputCost = 0.0;

        TokenUsage player0AccumulatedTokens = TokenUsage.zero();
        TokenUsage player1AccumulatedTokens = TokenUsage.zero();

        GameState lastSuccessfulTurnState = null;
        GameState pendingStateAfterAction = null;
        boolean lastActionWasSuccessful = false;

        long player0AccumulatedTimeMs = 0L;
        long player1AccumulatedTimeMs = 0L;

        try (BufferedReader reader = Files.newBufferedReader(logFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank())
                    continue;

                JsonNode node = objectMapper.readTree(line);

                // Determine event type by checking for type-specific fields
                if (node.has("player0Model")) {
                    // GameStartedEvent
                    originalGameId = node.get("gameId").asText();
                    player0Model = resolveModelId(node.get("player0Model").asText());
                    player1Model = resolveModelId(node.get("player1Model").asText());
                    player0Name = node.has("player0Name") ? node.get("player0Name").asText() : node.get("player0Model").asText();
                    player1Name = node.has("player1Name") ? node.get("player1Name").asText() : node.get("player1Model").asText();
                    player0InputCost = node.has("player0InputCost") ? node.get("player0InputCost").asDouble(0.0) : 0.0;
                    player0OutputCost = node.has("player0OutputCost") ? node.get("player0OutputCost").asDouble(0.0) : 0.0;
                    player1InputCost = node.has("player1InputCost") ? node.get("player1InputCost").asDouble(0.0) : 0.0;
                    player1OutputCost = node.has("player1OutputCost") ? node.get("player1OutputCost").asDouble(0.0) : 0.0;
                    logger.debug("Found GameStartedEvent: gameId={}, models={}/{}, names={}/{}",
                            originalGameId, player0Model, player1Model, player0Name, player1Name);

                } else if (node.has("gameState") && node.has("turn")) {
                    // TurnStartedEvent
                    GameState turnState = objectMapper.treeToValue(
                            node.get("gameState"), GameState.class);

                    if (lastActionWasSuccessful) {
                        // This is the state after the last successful action
                        lastSuccessfulTurnState = turnState;
                        lastActionWasSuccessful = false;
                    }
                    // Always track the pending state in case we need a fallback
                    pendingStateAfterAction = turnState;

                    logger.debug("Found TurnStartedEvent: turn={}, player={}",
                            node.get("turn").asInt(), node.get("playerIndex").asInt());

                } else if (node.has("action") && node.has("success")) {
                    // ActionEvent
                    boolean success = node.get("success").asBoolean();
                    if (success) {
                        lastActionWasSuccessful = true;
                        int playerIndex = node.get("playerIndex").asInt();
                        long duration = node.has("durationMs") ? node.get("durationMs").asLong() : 0L;
                        if (playerIndex == 0) {
                            player0AccumulatedTimeMs += duration;
                        } else if (playerIndex == 1) {
                            player1AccumulatedTimeMs += duration;
                        }
                        logger.debug("Found successful ActionEvent for player {}, duration: {} ms",
                                playerIndex, duration);
                    }
                } else if (node.has("reasoning") && node.has("tokenUsage")) {
                    // ReasoningEvent
                    int playerIndex = node.get("playerIndex").asInt();
                    JsonNode usageNode = node.get("tokenUsage");
                    long prompt = usageNode.path("promptTokens").asLong(0);
                    long completion = usageNode.path("completionTokens").asLong(0);
                    double cost = usageNode.path("cost").asDouble(0.0);
                    TokenUsage usage = new TokenUsage(prompt, completion, cost);
                    if (playerIndex == 0) {
                        player0AccumulatedTokens = player0AccumulatedTokens.add(usage);
                    } else if (playerIndex == 1) {
                        player1AccumulatedTokens = player1AccumulatedTokens.add(usage);
                    }
                }
            }
        }

        if (player0Model == null || player1Model == null) {
            throw new IOException("Could not find GameStartedEvent with model information");
        }

        // Determine which state to resume from
        GameState resumeState;
        if (lastSuccessfulTurnState != null) {
            resumeState = lastSuccessfulTurnState;
            logger.info("Resuming from turn {} (after last successful action)",
                    resumeState.turnNumber());
        } else if (pendingStateAfterAction != null) {
            resumeState = pendingStateAfterAction;
            logger.info("Resuming from turn {} (last available state)",
                    resumeState.turnNumber());
        } else {
            throw new IOException("Could not find any TurnStartedEvent to resume from");
        }

        return new ResumeData(originalGameId, player0Model, player1Model, player0Name, player1Name, resumeState,
                player0AccumulatedTimeMs, player1AccumulatedTimeMs,
                player0InputCost, player0OutputCost, player1InputCost, player1OutputCost,
                player0AccumulatedTokens, player1AccumulatedTokens);
    }
}
