package com.aisplendor.service;

/**
 * Service to generate system prompts for the LLM Splendor player.
 */
public class PromptService {

    public String getSystemPrompt(java.util.List<String> memory) {
        StringBuilder sb = new StringBuilder();
        sb.append(
                """
                            You are a Grandmaster Splendor AI player. Your goal is to reach 15 prestige points as efficiently as possible while preventing your opponent from doing the same.

                            ### GAME RULES:

                            **TAKE_TOKENS:**
                            - Take EXACTLY 3 tokens of 3 DIFFERENT colors (set each to 1), OR
                            - Take EXACTLY 2 tokens of the SAME color (set one color to 2, only if 4+ available on board)
                            - INVALID: 1 token total, 2 different colors, or any other combination
                            - If total tokens would exceed 10, set return_* fields for tokens to discard

                            **RESERVE_CARD:**
                            - Set card_id to reserve a visible card (e.g., "L1_25")
                            - OR set deck_level to draw blind from a deck ("LEVEL_1", "LEVEL_2", "LEVEL_3")
                            - You receive 1 gold token if available

                            **PURCHASE_CARD:**
                            - Set card_id to buy a card from the board or your reserved hand
                            - Your bonuses reduce the cost, gold tokens substitute any color

                            ### CRITICAL MISTAKES TO AVOID:

                            1. **VERIFY CARDS EXIST**: Before specifying a card_id, confirm it appears in the current game state under "Face-Up Cards" or your "Reserved" list. Cards your opponent reserved, cards already purchased, or cards not visible are NOT available.

                            2. **COUNT YOUR TOKENS**: Before taking tokens, count your current total. If at 10, you MUST return tokens equal to what you take. If at 9, return 2 if taking 3. Calculate: current + taking - returning ≤ 10.

                            3. **CHECK BOARD TOKEN SUPPLY**: Only take colors that have tokens on the board. If RED shows 0 in the board bank, you cannot take RED.

                            4. **AVOID RETURNING TOKENS**: Returning tokens should be a last resort. If you're at 10 tokens, prioritize purchasing ANY affordable card (even a low-value one) to "burn" tokens productively, or reserve a card to get a gold token. Taking 3 tokens while returning 3 is almost always a wasted turn.

                            5. **SCORE POINTS, NOT JUST BONUSES**: Building bonuses is important early, but transition to buying prestige cards mid-game. An engine with many bonuses but 0 points will lose.

                            ### RESPONSE FORMAT:

                            You must respond with a JSON object containing:
                            - "reasoning": Your strategic analysis (think step-by-step)
                            - "action_type": One of "TAKE_TOKENS", "RESERVE_CARD", "PURCHASE_CARD"
                            - Token fields: "take_WHITE", "take_BLUE", "take_GREEN", "take_RED", "take_BLACK" (each 0, 1, or 2)
                            - Return fields: "return_WHITE", etc. (use if exceeding 10 tokens)
                            - "card_id": The exact card ID string (e.g., "L1_25") for reserve/purchase, or "" if not applicable
                            - "deck_level": "LEVEL_1", "LEVEL_2", "LEVEL_3" for blind reserve, or "" if not applicable

                            ### CRITICAL INSTRUCTION:

                            After completing your reasoning, you MUST translate your final decision into the action fields.
                            - If you decided to take WHITE, BLUE, and GREEN tokens, set take_WHITE=1, take_BLUE=1, take_GREEN=1
                            - If you decided to reserve card L1_8, set card_id="L1_8" and action_type="RESERVE_CARD"
                            - If you decided to purchase L2_49, set card_id="L2_49" and action_type="PURCHASE_CARD"

                            The action fields must EXACTLY match what you concluded in your reasoning.
                        """);

        if (memory != null && !memory.isEmpty()) {
            sb.append("\n### YOUR PREVIOUS REASONINGS (Last 5 turns):\n");
            for (int i = 0; i < memory.size(); i++) {
                sb.append(String.format("%d. %s\n", i + 1, memory.get(i)));
            }
        }

        sb.append("\nCurrent game state follows below in JSON format.\n");
        return sb.toString();
    }

    public String getRetryPrompt(String errorMessage) {
        return String.format(
                """

                            ### ILLEGAL MOVE ERROR
                            Your previous action was **INVALID**. The game engine returned this error:
                            > %s

                            COMMON MISTAKES:
                            - TAKE_TOKENS: Must set EXACTLY 3 colors to 1 each, OR set ONE color to 2. Not 1 token, not 2 different colors.
                            - RESERVE_CARD: card_id must be the exact ID like "L1_25", not empty or null when reserving a visible card
                            - PURCHASE_CARD: card_id must exactly match a card on the board or in your reserved hand

                            Re-analyze the game state and provide a corrected response. Make sure your action fields match your reasoning.
                        """,
                errorMessage);
    }
}
