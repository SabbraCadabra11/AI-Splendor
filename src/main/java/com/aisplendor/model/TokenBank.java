package com.aisplendor.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Represents a collection of gem tokens.
 *
 * @param counts Map of Color to Integer representing the count of tokens for
 *               each color.
 */
public record TokenBank(Map<Color, Integer> counts) {
    public TokenBank {
        // Validation: No negative integers
        counts.forEach((color, count) -> {
            if (count < 0) {
                throw new IllegalArgumentException("Token count for " + color + " cannot be negative: " + count);
            }
        });
        // Defensive copy
        var copy = new EnumMap<Color, Integer>(Color.class);
        copy.putAll(counts);
        counts = Collections.unmodifiableMap(copy);
    }

    public int getCount(Color color) {
        return counts.getOrDefault(color, 0);
    }

    public int totalTokens() {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }
}
