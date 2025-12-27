package com.aisplendor.model;

import java.util.Map;

/**
 * Represents a Development Card.
 *
 * @param id             Unique identifier.
 * @param level          Card level (1, 2, or 3).
 * @param bonusGem       The gem provided by the card.
 * @param prestigePoints Points awarded by the card.
 * @param cost           Cost to purchase the card.
 */
public record DevelopmentCard(
        String id,
        CardLevel level,
        Color bonusGem,
        int prestigePoints,
        Map<Color, Integer> cost) {
}
