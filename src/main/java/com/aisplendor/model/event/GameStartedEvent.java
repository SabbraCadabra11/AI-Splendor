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
        String player0Name,
        String player1Name,
        double player0InputCost,
        double player0OutputCost,
        double player1InputCost,
        double player1OutputCost,
        GameState initialState) implements GameEvent {

    @Override
    public String eventType() {
        return "GAME_STARTED";
    }
}
