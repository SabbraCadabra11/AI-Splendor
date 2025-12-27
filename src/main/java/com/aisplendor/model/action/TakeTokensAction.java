package com.aisplendor.model.action;

import com.aisplendor.model.Color;
import java.util.Map;

/**
 * Action to take gem tokens from the bank.
 * Covers both taking 3 different tokens or 2 of the same token.
 * Also handles returning tokens if the limit is exceeded.
 *
 * @param tokens         Map of Color to Integer representing tokens to take.
 * @param tokensToReturn Map of Color to Integer representing tokens to return
 *                       (discard).
 */
public record TakeTokensAction(
        Map<Color, Integer> tokens,
        Map<Color, Integer> tokensToReturn) implements GameAction {
}
