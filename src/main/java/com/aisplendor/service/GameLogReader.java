package com.aisplendor.service;

import com.aisplendor.model.GameState;
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
            GameState resumeState) {
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

        GameState lastSuccessfulTurnState = null;
        GameState pendingStateAfterAction = null;
        boolean lastActionWasSuccessful = false;

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
                    player0Model = node.get("player0Model").asText();
                    player1Model = node.get("player1Model").asText();
                    logger.debug("Found GameStartedEvent: gameId={}, models={}/{}",
                            originalGameId, player0Model, player1Model);

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
                        logger.debug("Found successful ActionEvent for player {}",
                                node.get("playerIndex").asInt());
                    }
                }
                // Ignore ReasoningEvent, RetryEvent, GameEndedEvent
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

        return new ResumeData(originalGameId, player0Model, player1Model, resumeState);
    }
}
