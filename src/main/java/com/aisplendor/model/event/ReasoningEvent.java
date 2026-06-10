package com.aisplendor.model.event;

import com.aisplendor.model.TokenUsage;
import java.time.Instant;

/**
 * Event emitted when an LLM provides its reasoning for an action.
 */
public record ReasoningEvent(
        Instant timestamp,
        int playerIndex,
        String reasoning,
        TokenUsage tokenUsage) implements GameEvent {

    @Override
    public String eventType() {
        return "REASONING";
    }
}
