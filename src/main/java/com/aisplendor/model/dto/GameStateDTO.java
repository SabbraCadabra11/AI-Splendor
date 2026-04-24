package com.aisplendor.model.dto;

import com.aisplendor.model.GameState;
import com.aisplendor.model.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for GameState used in NDJSON logging.
 * Excludes sensitive information like deck contents.
 * Note: This DTO is no longer sent directly to LLMs — the compact text
 * format from CompactStateSerializer is used instead.
 */
public record GameStateDTO(
        BoardDTO board,
        List<PlayerDTO> players,
        int currentPlayerIndex,
        int turnNumber) {

    /**
     * Creates a GameStateDTO from a GameState.
     * Reasoning history is excluded from all players — it is managed
     * separately in the system prompt.
     *
     * @param state The full game state.
     * @return A sanitized DTO.
     */
    public static GameStateDTO fromGameState(GameState state) {
        List<PlayerDTO> playerDTOs = new ArrayList<>();

        for (Player player : state.players()) {
            playerDTOs.add(PlayerDTO.fromPlayer(player));
        }

        return new GameStateDTO(
                BoardDTO.fromBoard(state.board()),
                playerDTOs,
                state.currentPlayerIndex(),
                state.turnNumber());
    }
}
