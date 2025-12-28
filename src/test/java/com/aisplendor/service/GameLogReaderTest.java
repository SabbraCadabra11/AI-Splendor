package com.aisplendor.service;

import com.aisplendor.model.GameState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GameLogReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void parseLogForResume_extractsModelsAndState() throws IOException {
        // Create minimal test log with GameStartedEvent, TurnStartedEvent, and
        // ActionEvent
        String logContent = """
                {"timestamp":"2025-01-01T00:00:00Z","gameId":"test_game","player0Model":"model-a","player1Model":"model-b","initialState":{"board":{"availableTokens":{"counts":{"WHITE":4,"BLUE":4,"GREEN":4,"RED":4,"BLACK":4,"GOLD":5}},"faceUpCards":{"LEVEL_1":[],"LEVEL_2":[],"LEVEL_3":[]},"decks":{"LEVEL_1":[],"LEVEL_2":[],"LEVEL_3":[]},"availableNobles":[]},"players":[{"id":0,"tokens":{"counts":{}},"purchasedCards":[],"reservedCards":[],"visitedNobles":[],"score":0,"bonuses":{},"reasoningHistory":[]},{"id":1,"tokens":{"counts":{}},"purchasedCards":[],"reservedCards":[],"visitedNobles":[],"score":0,"bonuses":{},"reasoningHistory":[]}],"currentPlayerIndex":0,"turnNumber":1,"isGameOver":false,"winnerReason":null}}
                {"timestamp":"2025-01-01T00:00:01Z","turn":1,"playerIndex":0,"gameState":{"board":{"availableTokens":{"counts":{"WHITE":4,"BLUE":4,"GREEN":4,"RED":4,"BLACK":4,"GOLD":5}},"faceUpCards":{"LEVEL_1":[],"LEVEL_2":[],"LEVEL_3":[]},"decks":{"LEVEL_1":[],"LEVEL_2":[],"LEVEL_3":[]},"availableNobles":[]},"players":[{"id":0,"tokens":{"counts":{}},"purchasedCards":[],"reservedCards":[],"visitedNobles":[],"score":0,"bonuses":{},"reasoningHistory":[]},{"id":1,"tokens":{"counts":{}},"purchasedCards":[],"reservedCards":[],"visitedNobles":[],"score":0,"bonuses":{},"reasoningHistory":[]}],"currentPlayerIndex":0,"turnNumber":1,"isGameOver":false,"winnerReason":null}}
                {"timestamp":"2025-01-01T00:00:02Z","playerIndex":0,"reasoning":"Test reasoning"}
                {"timestamp":"2025-01-01T00:00:03Z","playerIndex":0,"action":{"type":"TAKE_TOKENS","tokens":{"RED":1,"BLUE":1,"GREEN":1},"tokensToReturn":null},"success":true}
                {"timestamp":"2025-01-01T00:00:04Z","turn":1,"playerIndex":1,"gameState":{"board":{"availableTokens":{"counts":{"WHITE":4,"BLUE":3,"GREEN":3,"RED":3,"BLACK":4,"GOLD":5}},"faceUpCards":{"LEVEL_1":[],"LEVEL_2":[],"LEVEL_3":[]},"decks":{"LEVEL_1":[],"LEVEL_2":[],"LEVEL_3":[]},"availableNobles":[]},"players":[{"id":0,"tokens":{"counts":{"RED":1,"BLUE":1,"GREEN":1}},"purchasedCards":[],"reservedCards":[],"visitedNobles":[],"score":0,"bonuses":{},"reasoningHistory":["Test reasoning"]},{"id":1,"tokens":{"counts":{}},"purchasedCards":[],"reservedCards":[],"visitedNobles":[],"score":0,"bonuses":{},"reasoningHistory":[]}],"currentPlayerIndex":1,"turnNumber":1,"isGameOver":false,"winnerReason":null}}
                """;

        Path logFile = tempDir.resolve("test_game.json");
        Files.writeString(logFile, logContent);

        GameLogReader reader = new GameLogReader();
        GameLogReader.ResumeData resumeData = reader.parseLogForResume(logFile);

        // Verify model extraction
        assertEquals("test_game", resumeData.originalGameId());
        assertEquals("model-a", resumeData.player0Model());
        assertEquals("model-b", resumeData.player1Model());

        // Verify we got the state after the successful action (player 1's turn)
        GameState state = resumeData.resumeState();
        assertNotNull(state);
        assertEquals(1, state.currentPlayerIndex(), "Should resume at player 1's turn");
        assertEquals(1, state.turnNumber());

        // Verify player 0 has tokens from their action
        assertEquals(1, state.players().get(0).tokens().getCount(
                com.aisplendor.model.Color.RED));
    }

    @Test
    void parseLogForResume_fallsBackToLastTurnIfNoActionFollows() throws IOException {
        // Log interrupted mid-turn (no ActionEvent for player 0's turn)
        String logContent = """
                {"timestamp":"2025-01-01T00:00:00Z","gameId":"test_game","player0Model":"model-a","player1Model":"model-b","initialState":{"board":{"availableTokens":{"counts":{"WHITE":4,"BLUE":4,"GREEN":4,"RED":4,"BLACK":4,"GOLD":5}},"faceUpCards":{"LEVEL_1":[],"LEVEL_2":[],"LEVEL_3":[]},"decks":{"LEVEL_1":[],"LEVEL_2":[],"LEVEL_3":[]},"availableNobles":[]},"players":[{"id":0,"tokens":{"counts":{}},"purchasedCards":[],"reservedCards":[],"visitedNobles":[],"score":0,"bonuses":{},"reasoningHistory":[]},{"id":1,"tokens":{"counts":{}},"purchasedCards":[],"reservedCards":[],"visitedNobles":[],"score":0,"bonuses":{},"reasoningHistory":[]}],"currentPlayerIndex":0,"turnNumber":1,"isGameOver":false,"winnerReason":null}}
                {"timestamp":"2025-01-01T00:00:01Z","turn":1,"playerIndex":0,"gameState":{"board":{"availableTokens":{"counts":{"WHITE":4,"BLUE":4,"GREEN":4,"RED":4,"BLACK":4,"GOLD":5}},"faceUpCards":{"LEVEL_1":[],"LEVEL_2":[],"LEVEL_3":[]},"decks":{"LEVEL_1":[],"LEVEL_2":[],"LEVEL_3":[]},"availableNobles":[]},"players":[{"id":0,"tokens":{"counts":{}},"purchasedCards":[],"reservedCards":[],"visitedNobles":[],"score":0,"bonuses":{},"reasoningHistory":[]},{"id":1,"tokens":{"counts":{}},"purchasedCards":[],"reservedCards":[],"visitedNobles":[],"score":0,"bonuses":{},"reasoningHistory":[]}],"currentPlayerIndex":0,"turnNumber":1,"isGameOver":false,"winnerReason":null}}
                """;

        Path logFile = tempDir.resolve("test_game_interrupted.json");
        Files.writeString(logFile, logContent);

        GameLogReader reader = new GameLogReader();
        GameLogReader.ResumeData resumeData = reader.parseLogForResume(logFile);

        // Should fall back to last available TurnStartedEvent
        GameState state = resumeData.resumeState();
        assertNotNull(state);
        assertEquals(0, state.currentPlayerIndex(), "Should resume at player 0's turn (replay)");
    }
}
