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
 * Reasoning history is excluded from this DTO — it is included separately
 * in the system prompt to avoid duplication.
 */
public record PlayerDTO(
        int id,
        TokenBank tokens,
        List<DevelopmentCard> purchasedCards,
        List<DevelopmentCard> reservedCards,
        List<NobleTile> visitedNobles,
        int score,
        Map<Color, Integer> bonuses) {

    /**
     * Creates a PlayerDTO from a Player.
     * Reasoning history is intentionally excluded — it is managed
     * by PromptService and included in the system prompt instead.
     *
     * @param player The full player state.
     * @return A DTO without reasoning history.
     */
    public static PlayerDTO fromPlayer(Player player) {
        return new PlayerDTO(
                player.id(),
                player.tokens(),
                player.purchasedCards(),
                player.reservedCards(),
                player.visitedNobles(),
                player.score(),
                player.bonuses());
    }
}
