package com.aisplendor.model.action;

/**
 * Action to purchase a development card.
 *
 * @param cardId The ID of the card to purchase (from board or reserved hand).
 */
public record PurchaseCardAction(
        String cardId) implements GameAction {
}
