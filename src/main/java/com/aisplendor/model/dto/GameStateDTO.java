package com.aisplendor.model.dto;

import com.aisplendor.model.GameState;
import com.aisplendor.model.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for GameState to be sent to LLMs.
 * Excludes sensitive information like deck contents and opponent reasoning
 * history.
 */
public record GameStateDTO(
        BoardDTO board,
        List<PlayerDTO> players,
        int currentPlayerIndex,
        int turnNumber) {

    /**
     * Creates a GameStateDTO from a GameState.
     * Includes reasoning history only for the current player.
     *
     * @param state The full game state.
     * @return A sanitized DTO for LLM consumption.
     */
    public static GameStateDTO fromGameState(GameState state) {
        int currentPlayerIndex = state.currentPlayerIndex();
        List<PlayerDTO> playerDTOs = new ArrayList<>();

        for (int i = 0; i < state.players().size(); i++) {
            Player player = state.players().get(i);
            if (i == currentPlayerIndex) {
                // Include reasoning history for the current player
                playerDTOs.add(PlayerDTO.fromPlayerWithHistory(player));
            } else {
                // Exclude reasoning history for opponents
                playerDTOs.add(PlayerDTO.fromPlayerWithoutHistory(player));
            }
        }

        return new GameStateDTO(
                BoardDTO.fromBoard(state.board()),
                playerDTOs,
                state.currentPlayerIndex(),
                state.turnNumber());
    }
}
