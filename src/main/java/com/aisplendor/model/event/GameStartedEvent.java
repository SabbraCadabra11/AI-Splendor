package com.aisplendor.model.event;

import com.aisplendor.model.GameState;
import java.time.Instant;

/**
 * Event emitted when a new game starts.
 */
public record GameStartedEvent(
        Instant timestamp,
        String gameId,
        String player0Model,
        String player1Model,
        GameState initialState) implements GameEvent {

    @Override
    public String eventType() {
        return "GAME_STARTED";
    }
}
