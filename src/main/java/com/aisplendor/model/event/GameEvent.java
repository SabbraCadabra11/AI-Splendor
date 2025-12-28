package com.aisplendor.model.event;

import java.time.Instant;

/**
 * Sealed interface for typed game events used in structured logging.
 * Each event is serialized as a single JSON line in NDJSON format.
 */
public sealed interface GameEvent permits
        GameStartedEvent,
        TurnStartedEvent,
        ReasoningEvent,
        ActionEvent,
        RetryEvent,
        GameEndedEvent {

    /**
     * @return The event type identifier for JSON serialization.
     */
    String eventType();

    /**
     * @return The timestamp when this event occurred.
     */
    Instant timestamp();
}
