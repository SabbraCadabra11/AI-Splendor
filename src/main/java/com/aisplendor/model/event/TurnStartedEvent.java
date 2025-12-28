package com.aisplendor.model.event;

import com.aisplendor.model.GameState;
import java.time.Instant;

/**
 * Event emitted at the start of each turn with a full game state snapshot.
 */
public record TurnStartedEvent(
        Instant timestamp,
        int turn,
        int playerIndex,
        GameState gameState) implements GameEvent {

    @Override
    public String eventType() {
        return "TURN_STARTED";
    }
}
