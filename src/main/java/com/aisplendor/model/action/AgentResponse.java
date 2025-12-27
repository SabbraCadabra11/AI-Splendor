package com.aisplendor.model.action;

/**
 * Represents the structured response from the LLM, containing its reasoning and
 * the selected action.
 */
public record AgentResponse(String reasoning, GameAction action) {
}
