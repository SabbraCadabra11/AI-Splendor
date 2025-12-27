package com.aisplendor.model.action;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Sealed interface representing a generic action that a player can take.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TakeTokensAction.class, name = "TAKE_TOKENS"),
        @JsonSubTypes.Type(value = ReserveCardAction.class, name = "RESERVE_CARD"),
        @JsonSubTypes.Type(value = PurchaseCardAction.class, name = "PURCHASE_CARD")
})
public sealed interface GameAction permits TakeTokensAction, ReserveCardAction, PurchaseCardAction {
}
