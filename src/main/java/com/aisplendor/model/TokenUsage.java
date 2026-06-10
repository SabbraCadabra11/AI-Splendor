package com.aisplendor.model;

/**
 * Represents the token usage stats and cost associated with an LLM call or a game.
 */
public record TokenUsage(long promptTokens, long completionTokens, double cost) {
    public static TokenUsage zero() {
        return new TokenUsage(0, 0, 0.0);
    }

    public TokenUsage add(TokenUsage other) {
        if (other == null) {
            return this;
        }
        return new TokenUsage(
            this.promptTokens + other.promptTokens,
            this.completionTokens + other.completionTokens,
            this.cost + other.cost
        );
    }
}
