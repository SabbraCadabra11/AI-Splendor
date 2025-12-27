package com.aisplendor.config;

/**
 * Configuration for LLM reasoning capabilities.
 * Not all models support reasoning - when disabled, no reasoning block is sent.
 *
 * @param enabled Whether to include the reasoning block in API requests
 * @param effort  Reasoning effort level: "low", "medium", or "high"
 * @param exclude Whether to exclude reasoning traces from the response
 */
public record ReasoningConfig(boolean enabled, String effort, boolean exclude) {

    /**
     * Default disabled configuration for models that don't support reasoning.
     */
    public static ReasoningConfig disabled() {
        return new ReasoningConfig(false, null, true);
    }
}
