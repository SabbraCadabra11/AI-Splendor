package com.aisplendor.model;

import java.util.List;

/**
 * Represents the entire state of the game.
 *
 * @param board              The game board.
 * @param players            List of players.
 * @param currentPlayerIndex Index of the current player in the players list.
 * @param turnNumber         Current turn number.
 * @param isGameOver         Validates if game is over.
 * @param winnerReason       Reason for the winner (if game over).
 */
public record GameState(
        Board board,
        List<Player> players,
        int currentPlayerIndex,
        int turnNumber,
        boolean isGameOver,
        String winnerReason) {
}
