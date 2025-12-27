package com.aisplendor.model;

import java.util.Map;

/**
 * Represents a Noble Tile.
 *
 * @param id             Unique identifier.
 * @param prestigePoints Points awarded (Always 3).
 * @param requirement    Bonus gems required to visit.
 */
public record NobleTile(
        String id,
        int prestigePoints,
        Map<Color, Integer> requirement) {
}
