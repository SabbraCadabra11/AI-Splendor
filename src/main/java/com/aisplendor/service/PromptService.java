package com.aisplendor.service;

import com.aisplendor.config.StageConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service to generate system prompts for the LLM Splendor player.
 * Loads prompt templates from resource files and injects stage context.
 */
public class PromptService {

    private static final String BASE_PROMPT_PATH = "prompts/system_prompt.txt";
    private static final String LEG1_CONTEXT_PATH = "prompts/stage_context_leg1.txt";
    private static final String LEG2_CONTEXT_PATH = "prompts/stage_context_leg2.txt";

    private final String basePromptTemplate;
    private final String leg1ContextTemplate;
    private final String leg2ContextTemplate;

    public PromptService() {
        this.basePromptTemplate = loadResource(BASE_PROMPT_PATH);
        this.leg1ContextTemplate = loadResource(LEG1_CONTEXT_PATH);
        this.leg2ContextTemplate = loadResource(LEG2_CONTEXT_PATH);
    }

    /**
     * Generate system prompt with stage context for the specified player.
     * 
     * @param memory      The player's reasoning history
     * @param stageConfig The knockout stage configuration (or StageConfig.none())
     * @param playerIndex The current player's index (0 or 1)
     * @return The complete system prompt
     */
    public String getSystemPrompt(List<String> memory, StageConfig stageConfig, int playerIndex) {
        String stageContext = buildStageContext(stageConfig, playerIndex);
        String prompt = basePromptTemplate.replace("{STAGE_CONTEXT}", stageContext);

        StringBuilder sb = new StringBuilder(prompt);

        if (memory != null && !memory.isEmpty()) {
            sb.append(String.format("\n### YOUR PREVIOUS REASONINGS (Last %d turns):\n", memory.size()));
            for (int i = 0; i < memory.size(); i++) {
                sb.append(String.format("%d. %s\n", i + 1, memory.get(i)));
            }
        }

        sb.append("\nCurrent game state follows below.\n");
        return sb.toString();
    }

    /**
     * Backward-compatible version without stage context.
     */
    public String getSystemPrompt(List<String> memory) {
        return getSystemPrompt(memory, StageConfig.none(), 0);
    }

    private String buildStageContext(StageConfig config, int playerIndex) {
        if (!config.hasStage()) {
            return "";
        }

        if (!config.isSecondLeg()) {
            // First leg - simple context
            return "\n" + leg1ContextTemplate.replace("{stage}", config.stage()) + "\n";
        }

        // Second leg - include first leg result and advancement requirement
        int myScore = config.getFirstLegScore(playerIndex);
        int oppScore = config.getFirstLegScore(1 - playerIndex);
        int diff = config.getPointDifference(playerIndex);

        String result = diff > 0 ? "won" : (diff < 0 ? "lost" : "drew");
        String requirement = buildAdvancementRequirement(diff);
        String tiebreakerStatus = buildTiebreakerStatus(config, playerIndex);

        String context = leg2ContextTemplate
                .replace("{stage}", config.stage())
                .replace("{result}", result)
                .replace("{yourScore}", String.valueOf(myScore))
                .replace("{opponentScore}", String.valueOf(oppScore))
                .replace("{requirement}", requirement)
                .replace("{tiebreakerStatus}", tiebreakerStatus);

        return "\n" + context + "\n";
    }

    private String buildAdvancementRequirement(int pointDifference) {
        if (pointDifference > 0) {
            // Leading - must not lose by more than the lead
            return "not lose by more than " + pointDifference + " points";
        } else if (pointDifference < 0) {
            // Trailing - must win by more than the deficit
            int needed = Math.abs(pointDifference) + 1;
            return "win by at least " + needed + " points";
        } else {
            // Tied - must win (or tie and have fewer cards)
            return "win this leg, or tie and purchase fewer cards than your opponent";
        }
    }

    private String buildTiebreakerStatus(StageConfig config, int playerIndex) {
        int myCards = config.getFirstLegCards(playerIndex);
        int oppCards = config.getFirstLegCards(1 - playerIndex);

        if (myCards == 0 && oppCards == 0) {
            return ""; // No card data available
        }

        return String.format("You bought %d cards in the first leg vs opponent's %d.", myCards, oppCards);
    }

    /**
     * Generate retry prompt that includes the previous invalid response.
     * Shows the model exactly what it submitted so it can identify and fix the
     * mismatch.
     * 
     * @param errorMessage     The validation error message
     * @param previousResponse The previous invalid response (reasoning + action
     *                         JSON), can be null
     * @return The retry prompt
     */
    public String getRetryPrompt(String errorMessage, String previousResponse) {
        StringBuilder sb = new StringBuilder();
        sb.append("""

                ### ILLEGAL MOVE ERROR
                Your previous action was **INVALID**. The game engine returned this error:
                > %s

                """.formatted(errorMessage));

        if (previousResponse != null && !previousResponse.isBlank()) {
            sb.append(
                    """
                            **YOUR PREVIOUS RESPONSE (which was rejected):**
                            ```
                            %s
                            ```

                            **CRITICAL**: Look at the action fields you submitted above. Your reasoning may have been correct, but the action fields did NOT match your conclusion. This is a common failure mode where you reason "I should purchase L1_14" but submit `action_type: TAKE_TOKENS` with empty token fields.

                            """
                            .formatted(previousResponse));
        }

        sb.append(
                """
                        COMMON MISTAKES:
                        - **REASONING/ACTION MISMATCH**: You reasoned correctly but forgot to set the action fields. If you concluded "purchase L1_14", you MUST set `action_type: "PURCHASE_CARD"` and `card_id: "L1_14"`.
                        - TAKE_TOKENS: Must set EXACTLY 3 colors to 1 each, OR set ONE color to 2. Not 1 token, not 2 different colors.
                        - RESERVE_CARD: card_id must be the exact ID like "L1_25", not empty or null when reserving a visible card.
                        - PURCHASE_CARD: card_id must exactly match a card on the board or in your reserved hand.

                        **OPTIONS**:
                        1. If your previous reasoning was sound, simply FIX the action fields to match your conclusion.
                        2. If the action you intended is actually illegal, analyze the game state again and choose a DIFFERENT legal action.

                        Provide a corrected response. Double-check that your action_type and related fields EXACTLY match your final decision.
                        """);

        return sb.toString();
    }

    /**
     * Backward-compatible version without previous response.
     */
    public String getRetryPrompt(String errorMessage) {
        return getRetryPrompt(errorMessage, null);
    }

    private String loadResource(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new RuntimeException("Resource not found: " + path);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load resource: " + path, e);
        }
    }
}
