package com.aisplendor.model.event;

import com.aisplendor.model.action.GameAction;
import java.time.Instant;

/**
 * Event emitted when a player action is executed.
 */
public record ActionEvent(
        Instant timestamp,
        int playerIndex,
        GameAction action,
        boolean success,
        Long durationMs) implements GameEvent {

    /**
     * Secondary constructor for backward compatibility.
     */
    public ActionEvent(Instant timestamp, int playerIndex, GameAction action, boolean success) {
        this(timestamp, playerIndex, action, success, null);
    }

    @Override
    public String eventType() {
        return "ACTION";
    }
}
