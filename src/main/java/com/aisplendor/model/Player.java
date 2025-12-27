package com.aisplendor.model;

import java.util.List;
import java.util.Map;

/**
 * Represents a Player in the game.
 *
 * @param id             Player ID (0 or 1).
 * @param tokens         Current tokens in hand.
 * @param purchasedCards List of cards purchased by the player.
 * @param reservedCards  List of cards reserved by the player (max 3).
 * @param visitedNobles  List of nobles that have visited the player.
 * @param score          Current score derived from cards and nobles.
 * @param bonuses        Map of bonuses derived from purchased cards.
 */
public record Player(
                int id,
                TokenBank tokens,
                List<DevelopmentCard> purchasedCards,
                List<DevelopmentCard> reservedCards,
                List<NobleTile> visitedNobles,
                int score,
                Map<Color, Integer> bonuses,
                List<String> reasoningHistory) {
}
