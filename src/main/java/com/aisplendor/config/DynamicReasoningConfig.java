package com.aisplendor.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for dynamic reasoning effort that changes based on turn number.
 * Parses phase rules from a string format like "1-5:medium,6+:high".
 *
 * Phase format:
 * - "N-M:effort" — applies effort for turns N through M (inclusive)
 * - "N+:effort" — applies effort for turn N onwards
 * - Comma-separated rules, last matching rule wins
 *
 * When dynamic reasoning is disabled, falls back to the static ReasoningConfig.
 */
public class DynamicReasoningConfig {

    private final boolean dynamic;
    private final List<PhaseRule> rules;
    private final ReasoningConfig staticConfig;

    /**
     * A single phase rule mapping a turn range to a reasoning effort level.
     */
    private record PhaseRule(int startTurn, int endTurn, String effort) {
        boolean matches(int turnNumber) {
            return turnNumber >= startTurn && turnNumber <= endTurn;
        }
    }

    /**
     * Creates a DynamicReasoningConfig.
     *
     * @param dynamic      Whether dynamic reasoning is enabled
     * @param phasesString The phase rules string (e.g., "1-5:medium,6+:high"), can be null
     * @param staticConfig The fallback static reasoning config
     */
    public DynamicReasoningConfig(boolean dynamic, String phasesString, ReasoningConfig staticConfig) {
        this.dynamic = dynamic;
        this.staticConfig = staticConfig;
        this.rules = dynamic ? parsePhases(phasesString) : List.of();
    }

    /**
     * Creates a non-dynamic config that always uses the static reasoning config.
     */
    public static DynamicReasoningConfig fromStatic(ReasoningConfig staticConfig) {
        return new DynamicReasoningConfig(false, null, staticConfig);
    }

    /**
     * Returns whether dynamic reasoning is enabled.
     */
    public boolean isDynamic() {
        return dynamic;
    }

    /**
     * Returns the static reasoning config (used when dynamic is disabled).
     */
    public ReasoningConfig getStaticConfig() {
        return staticConfig;
    }

    /**
     * Resolves the reasoning effort for a given turn number.
     * If dynamic is disabled or no rule matches, returns the static effort.
     * When multiple rules match, the last matching rule wins.
     *
     * @param turnNumber The current turn number
     * @return The effort level string ("medium", "high", etc.)
     */
    public String getEffortForTurn(int turnNumber) {
        if (!dynamic || !staticConfig.enabled()) {
            return staticConfig.effort();
        }

        // Last matching rule wins
        String effort = staticConfig.effort();
        for (PhaseRule rule : rules) {
            if (rule.matches(turnNumber)) {
                effort = rule.effort();
            }
        }
        return effort;
    }

    /**
     * Creates a ReasoningConfig for a specific turn, with the resolved effort level.
     *
     * @param turnNumber The current turn number
     * @return A ReasoningConfig with the appropriate effort for this turn
     */
    public ReasoningConfig resolveForTurn(int turnNumber) {
        if (!staticConfig.enabled()) {
            return staticConfig;
        }
        if (!dynamic) {
            return staticConfig;
        }
        String effort = getEffortForTurn(turnNumber);
        return new ReasoningConfig(true, effort, staticConfig.exclude());
    }

    /**
     * Parses a phases string into a list of PhaseRules.
     * Format: "1-5:medium,6+:high"
     */
    private static List<PhaseRule> parsePhases(String phasesString) {
        List<PhaseRule> rules = new ArrayList<>();
        if (phasesString == null || phasesString.isBlank()) {
            return rules;
        }

        String[] parts = phasesString.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;

            String[] rangeParts = part.split(":");
            if (rangeParts.length != 2) {
                throw new IllegalArgumentException(
                        "Invalid phase rule format: '" + part + "'. Expected 'range:effort' (e.g., '1-5:medium')");
            }

            String range = rangeParts[0].trim();
            String effort = rangeParts[1].trim().toLowerCase();

            if (range.endsWith("+")) {
                // Open-ended: "6+"
                int start = Integer.parseInt(range.substring(0, range.length() - 1));
                rules.add(new PhaseRule(start, Integer.MAX_VALUE, effort));
            } else if (range.contains("-")) {
                // Range: "1-5"
                String[] bounds = range.split("-");
                int start = Integer.parseInt(bounds[0].trim());
                int end = Integer.parseInt(bounds[1].trim());
                rules.add(new PhaseRule(start, end, effort));
            } else {
                // Single turn: "3"
                int turn = Integer.parseInt(range);
                rules.add(new PhaseRule(turn, turn, effort));
            }
        }

        return rules;
    }

    @Override
    public String toString() {
        if (!dynamic) {
            return "static:" + (staticConfig.enabled() ? staticConfig.effort() : "disabled");
        }
        StringBuilder sb = new StringBuilder("dynamic[");
        for (int i = 0; i < rules.size(); i++) {
            PhaseRule rule = rules.get(i);
            if (i > 0) sb.append(",");
            if (rule.endTurn() == Integer.MAX_VALUE) {
                sb.append(rule.startTurn()).append("+:").append(rule.effort());
            } else if (rule.startTurn() == rule.endTurn()) {
                sb.append(rule.startTurn()).append(":").append(rule.effort());
            } else {
                sb.append(rule.startTurn()).append("-").append(rule.endTurn()).append(":").append(rule.effort());
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
