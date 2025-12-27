package com.aisplendor.model.action;

import java.util.Map;

import com.aisplendor.model.CardLevel;
import com.aisplendor.model.Color;

/**
 * Action to reserve a development card.
 * Can reserve a specific visible card by ID or a blind card from a deck level.
 *
 * @param cardId         The ID of the visible card to reserve (nullable if
 *                       drawing from deck).
 * @param deckLevel      It drawing blindly from a deck, the level of the deck
 *                       (nullable if reserving specific card).
 * @param tokensToReturn Tokens to discard if the gold coin puts player over
 *                       limit (nullable).
 */
public record ReserveCardAction(
        String cardId,
        CardLevel deckLevel,
        Map<Color, Integer> tokensToReturn) implements GameAction {
}
