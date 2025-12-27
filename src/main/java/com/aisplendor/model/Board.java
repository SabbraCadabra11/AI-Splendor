package com.aisplendor.model;

import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Represents the shared Game Board.
 *
 * @param availableTokens Tokens currently available on the board.
 * @param faceUpCards     Cards currently visible on the board (mapped by
 *                        level).
 * @param decks           Remaining cards in the decks (mapped by level).
 * @param availableNobles Nobles currently waiting to visit.
 */
public record Board(
        TokenBank availableTokens,
        Map<CardLevel, List<DevelopmentCard>> faceUpCards,
        Map<CardLevel, Queue<DevelopmentCard>> decks,
        List<NobleTile> availableNobles) {
}
