package com.aisplendor.config;

/**
 * Configuration for knockout stage context.
 * Used to inject tournament awareness into the AI system prompt.
 */
public record StageConfig(
        String stage, // e.g., "quarter-final", "semi-final", "final"
        int leg, // 1 or 2
        int player0FirstLegScore, // Player 0's score from first leg (only for leg 2)
        int player1FirstLegScore, // Player 1's score from first leg (only for leg 2)
        int player0FirstLegCards, // Player 0's cards bought in first leg (for tiebreaker)
        int player1FirstLegCards, // Player 1's cards bought in first leg (for tiebreaker)
        boolean swappedStartingPlayer // If true, player indices are swapped vs first leg
) {
    /**
     * Returns a StageConfig indicating no knockout stage context.
     */
    public static StageConfig none() {
        return new StageConfig(null, 0, 0, 0, 0, 0, false);
    }

    /**
     * Returns true if this config has valid stage information.
     */
    public boolean hasStage() {
        return stage != null && !stage.isBlank() && leg > 0;
    }

    /**
     * Returns true if this is the second leg (first leg scores are relevant).
     */
    public boolean isSecondLeg() {
        return leg == 2;
    }

    /**
     * Get the first leg score for the specified player.
     * Accounts for swapped starting player if configured.
     */
    public int getFirstLegScore(int playerIndex) {
        int effectiveIndex = swappedStartingPlayer ? (1 - playerIndex) : playerIndex;
        return effectiveIndex == 0 ? player0FirstLegScore : player1FirstLegScore;
    }

    /**
     * Get the first leg cards bought for the specified player.
     * Accounts for swapped starting player if configured.
     */
    public int getFirstLegCards(int playerIndex) {
        int effectiveIndex = swappedStartingPlayer ? (1 - playerIndex) : playerIndex;
        return effectiveIndex == 0 ? player0FirstLegCards : player1FirstLegCards;
    }

    /**
     * Get the point difference from the perspective of the specified player.
     * Positive means player is leading, negative means trailing.
     */
    public int getPointDifference(int playerIndex) {
        int myScore = getFirstLegScore(playerIndex);
        int oppScore = getFirstLegScore(1 - playerIndex);
        return myScore - oppScore;
    }

    /**
     * Get the card difference from the perspective of the specified player.
     * Negative means player bought fewer cards (better for tiebreaker).
     */
    public int getCardDifference(int playerIndex) {
        int myCards = getFirstLegCards(playerIndex);
        int oppCards = getFirstLegCards(1 - playerIndex);
        return myCards - oppCards;
    }
}
