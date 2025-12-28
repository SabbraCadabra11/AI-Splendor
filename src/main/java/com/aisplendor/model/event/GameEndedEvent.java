package com.aisplendor.model.event;

import java.time.Instant;
import java.util.Map;

/**
 * Event emitted when the game ends.
 */
public record GameEndedEvent(
        Instant timestamp,
        Integer winnerIndex,
        String winnerReason,
        Map<Integer, Integer> finalScores) implements GameEvent {

    @Override
    public String eventType() {
        return "GAME_ENDED";
    }
}
