package com.aisplendor.model.event;

import java.time.Instant;

/**
 * Event emitted when an LLM action fails validation and a retry is attempted.
 */
public record RetryEvent(
        Instant timestamp,
        int playerIndex,
        int attempt,
        String error) implements GameEvent {

    @Override
    public String eventType() {
        return "RETRY";
    }
}
