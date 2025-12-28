package com.aisplendor.model.dto;

import com.aisplendor.model.Board;
import com.aisplendor.model.CardLevel;
import com.aisplendor.model.DevelopmentCard;
import com.aisplendor.model.NobleTile;
import com.aisplendor.model.TokenBank;

import java.util.List;
import java.util.Map;

/**
 * DTO for Board to be sent to LLMs.
 * Excludes deck contents - players should only see face-up cards.
 */
public record BoardDTO(
        TokenBank availableTokens,
        Map<CardLevel, List<DevelopmentCard>> faceUpCards,
        List<NobleTile> availableNobles) {

    /**
     * Creates a BoardDTO from a Board.
     * Intentionally excludes decks.
     *
     * @param board The full board state.
     * @return A sanitized DTO without deck contents.
     */
    public static BoardDTO fromBoard(Board board) {
        return new BoardDTO(
                board.availableTokens(),
                board.faceUpCards(),
                board.availableNobles());
    }
}
