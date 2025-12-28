package com.aisplendor.model.dto;

import com.aisplendor.model.Color;
import com.aisplendor.model.DevelopmentCard;
import com.aisplendor.model.NobleTile;
import com.aisplendor.model.Player;
import com.aisplendor.model.TokenBank;

import java.util.List;
import java.util.Map;

/**
 * DTO for Player to be sent to LLMs.
 * Reasoning history is conditionally included - only for the current player.
 */
public record PlayerDTO(
        int id,
        TokenBank tokens,
        List<DevelopmentCard> purchasedCards,
        List<DevelopmentCard> reservedCards,
        List<NobleTile> visitedNobles,
        int score,
        Map<Color, Integer> bonuses,
        List<String> reasoningHistory) {

    /**
     * Creates a PlayerDTO from a Player, including reasoning history.
     * Use this for the current player who should see their own history.
     *
     * @param player The full player state.
     * @return A DTO with reasoning history included.
     */
    public static PlayerDTO fromPlayerWithHistory(Player player) {
        return new PlayerDTO(
                player.id(),
                player.tokens(),
                player.purchasedCards(),
                player.reservedCards(),
                player.visitedNobles(),
                player.score(),
                player.bonuses(),
                player.reasoningHistory());
    }

    /**
     * Creates a PlayerDTO from a Player, excluding reasoning history.
     * Use this for opponent players whose reasoning should be private.
     *
     * @param player The full player state.
     * @return A DTO without reasoning history.
     */
    public static PlayerDTO fromPlayerWithoutHistory(Player player) {
        return new PlayerDTO(
                player.id(),
                player.tokens(),
                player.purchasedCards(),
                player.reservedCards(),
                player.visitedNobles(),
                player.score(),
                player.bonuses(),
                null);
    }
}
