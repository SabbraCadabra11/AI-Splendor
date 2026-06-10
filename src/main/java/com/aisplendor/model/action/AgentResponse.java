package com.aisplendor.model.action;

import com.aisplendor.model.TokenUsage;

/**
 * Represents the structured response from the LLM, containing its reasoning,
 * the selected action, and token usage information.
 */
public record AgentResponse(String reasoning, GameAction action, TokenUsage tokenUsage) {
}
