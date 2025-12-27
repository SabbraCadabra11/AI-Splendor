package com.aisplendor.engine;

import com.aisplendor.model.*;
import com.aisplendor.model.action.TakeTokensAction;
import com.aisplendor.model.action.ReserveCardAction;
import com.aisplendor.model.action.PurchaseCardAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class GameEngineTest {

    private GameEngine engine;
    private GameState initialState;

    @BeforeEach
    void setUp() {
        engine = new GameEngine();

        // Setup standard board with 4 tokens each
        Map<Color, Integer> boardTokens = new HashMap<>();
        for (Color c : Color.values()) {
            if (c == Color.GOLD)
                boardTokens.put(c, 5);
            else
                boardTokens.put(c, 4);
        }
        TokenBank boardBank = new TokenBank(boardTokens);

        Board board = new Board(boardBank, new HashMap<>(), new HashMap<>(), new ArrayList<>());
        Player p1 = new Player(0, new TokenBank(Collections.emptyMap()), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), 0, new HashMap<>(), new ArrayList<>());

        initialState = new GameState(board, List.of(p1), 0, 1, false, null);
    }

    @Test
    void testTake3DifferentValid() {
        Map<Color, Integer> take = Map.of(Color.RED, 1, Color.BLUE, 1, Color.GREEN, 1);
        TakeTokensAction action = new TakeTokensAction(take, null);

        assertDoesNotThrow(() -> engine.validateAction(initialState, action));
    }

    @Test
    void testTake2SameValid() {
        Map<Color, Integer> take = Map.of(Color.RED, 2);
        TakeTokensAction action = new TakeTokensAction(take, null);

        assertDoesNotThrow(() -> engine.validateAction(initialState, action));
    }

    @Test
    void testTake2SameInvalid_NotEnoughTokens() {
        // Create state with only 3 RED tokens
        Map<Color, Integer> limitedTokens = new HashMap<>();
        for (Color c : Color.values())
            limitedTokens.put(c, c == Color.RED ? 3 : 4);

        Board limitedBoard = new Board(new TokenBank(limitedTokens), new HashMap<>(), new HashMap<>(),
                new ArrayList<>());
        GameState state = new GameState(limitedBoard, initialState.players(), 0, 1, false, null);

        Map<Color, Integer> take = Map.of(Color.RED, 2);
        TakeTokensAction action = new TakeTokensAction(take, null);

        Exception e = assertThrows(IllegalArgumentException.class, () -> engine.validateAction(state, action));
        assertTrue(e.getMessage().contains("need 4"));
    }

    @Test
    void testTakeGoldInvalid() {
        Map<Color, Integer> take = Map.of(Color.GOLD, 1, Color.BLUE, 1, Color.RED, 1);
        TakeTokensAction action = new TakeTokensAction(take, null);

        assertThrows(IllegalArgumentException.class, () -> engine.validateAction(initialState, action));
    }

    @Test
    void testReserveCardFromBoard() {
        // Mock a card on board
        Map<Color, Integer> cost = Map.of(Color.RED, 1);
        DevelopmentCard card = new DevelopmentCard("c1", CardLevel.LEVEL_1, Color.BLUE, 0, cost);

        List<DevelopmentCard> l1Cards = new ArrayList<>();
        l1Cards.add(card);

        Map<CardLevel, List<DevelopmentCard>> faceUp = new HashMap<>();
        faceUp.put(CardLevel.LEVEL_1, l1Cards);

        Board reserveBoard = new Board(initialState.board().availableTokens(), faceUp, new HashMap<>(),
                new ArrayList<>());
        GameState state = new GameState(reserveBoard, initialState.players(), 0, 1, false, null);

        ReserveCardAction action = new ReserveCardAction("c1", null, null);

        // Validate
        assertDoesNotThrow(() -> engine.validateAction(state, action));

        // Apply
        GameState newState = engine.applyAction(state, action);
        Player newPlayer = newState.players().get(0);

        assertEquals(1, newPlayer.reservedCards().size());
        assertEquals("c1", newPlayer.reservedCards().get(0).id());
        assertEquals(1, newPlayer.tokens().getCount(Color.GOLD)); // Should get 1 gold
    }

    @Test
    void testPurchaseCardValid() {
        // Setup state where player has tokens to buy a card
        Map<Color, Integer> cost = Map.of(Color.RED, 2);
        DevelopmentCard card = new DevelopmentCard("c1", CardLevel.LEVEL_1, Color.BLUE, 0, cost);

        List<DevelopmentCard> l1Cards = new ArrayList<>();
        l1Cards.add(card);
        Map<CardLevel, List<DevelopmentCard>> faceUp = new HashMap<>();
        faceUp.put(CardLevel.LEVEL_1, l1Cards);

        Player p1 = new Player(0, new TokenBank(Map.of(Color.RED, 2)), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), 0, new HashMap<>(), new ArrayList<>());

        Board board = new Board(initialState.board().availableTokens(), faceUp, new HashMap<>(), new ArrayList<>());
        GameState state = new GameState(board, List.of(p1), 0, 1, false, null);

        PurchaseCardAction action = new PurchaseCardAction("c1");

        assertDoesNotThrow(() -> engine.validateAction(state, action));

        GameState newState = engine.applyAction(state, action);
        Player updatedPlayer = newState.players().get(0);

        assertEquals(1, updatedPlayer.purchasedCards().size());
        assertEquals(0, updatedPlayer.tokens().getCount(Color.RED));
        assertEquals(1, updatedPlayer.bonuses().get(Color.BLUE));
    }

    @Test
    void testPurchaseCardWithGold() {
        Map<Color, Integer> cost = Map.of(Color.RED, 2);
        DevelopmentCard card = new DevelopmentCard("c1", CardLevel.LEVEL_1, Color.BLUE, 0, cost);

        List<DevelopmentCard> l1Cards = new ArrayList<>();
        l1Cards.add(card);
        Map<CardLevel, List<DevelopmentCard>> faceUp = new HashMap<>();
        faceUp.put(CardLevel.LEVEL_1, l1Cards);

        // Player has 1 RED and 1 GOLD
        Player p1 = new Player(0, new TokenBank(Map.of(Color.RED, 1, Color.GOLD, 1)), new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(), 0, new HashMap<>(), new ArrayList<>());

        Board board = new Board(initialState.board().availableTokens(), faceUp, new HashMap<>(), new ArrayList<>());
        GameState state = new GameState(board, List.of(p1), 0, 1, false, null);

        PurchaseCardAction action = new PurchaseCardAction("c1");

        assertDoesNotThrow(() -> engine.validateAction(state, action));

        GameState newState = engine.applyAction(state, action);
        Player updatedPlayer = newState.players().get(0);

        assertEquals(1, updatedPlayer.purchasedCards().size());
        assertEquals(0, updatedPlayer.tokens().getCount(Color.RED));
        assertEquals(0, updatedPlayer.tokens().getCount(Color.GOLD));
    }

    @Test
    void testNobleVisit() {
        // Setup state where player gets enough bonuses to attract a noble
        Map<Color, Integer> req = Map.of(Color.BLUE, 3);
        NobleTile noble = new NobleTile("n1", 3, req);

        Board board = new Board(initialState.board().availableTokens(), new HashMap<>(), new HashMap<>(),
                List.of(noble));

        Map<Color, Integer> bonuses = new HashMap<>();
        bonuses.put(Color.BLUE, 2); // Player has 2 bonuses already

        Player p1 = new Player(0, new TokenBank(Collections.emptyMap()), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), 0, bonuses, new ArrayList<>());

        // Player will take a card that gives the 3rd BLUE bonus
        Map<Color, Integer> cost = Map.of(Color.RED, 1);
        DevelopmentCard card = new DevelopmentCard("c1", CardLevel.LEVEL_1, Color.BLUE, 0, cost);

        List<DevelopmentCard> l1Cards = new ArrayList<>();
        l1Cards.add(card);
        Map<CardLevel, List<DevelopmentCard>> faceUp = new HashMap<>();
        faceUp.put(CardLevel.LEVEL_1, l1Cards);

        p1 = new Player(0, new TokenBank(Map.of(Color.RED, 1)), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), 0, bonuses, new ArrayList<>());

        Board updatedBoard = new Board(board.availableTokens(), faceUp, new HashMap<>(), List.of(noble));
        GameState state = new GameState(updatedBoard, List.of(p1), 0, 1, false, null);

        PurchaseCardAction action = new PurchaseCardAction("c1");
        GameState nextState = engine.applyAction(state, action);

        Player updatedPlayer = nextState.players().get(0);
        assertEquals(1, updatedPlayer.visitedNobles().size());
        assertEquals(3, updatedPlayer.score()); // 0 + 3 from noble
        assertTrue(nextState.board().availableNobles().isEmpty());
    }

    @Test
    void testGameEnd() {
        // Setup state where player reaches 15 points
        Player p1 = new Player(0, new TokenBank(Map.of(Color.RED, 1)), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), 15, new HashMap<>(), new ArrayList<>()); // Already has 15
        Player p2 = new Player(1, new TokenBank(Collections.emptyMap()), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), 0, new HashMap<>(), new ArrayList<>());

        // Player 1 just finished their turn (currentPlayerIndex will be 1)
        // Wait, applyAction increments player index.
        // If p1 takes tokens, nextPlayerIndex becomes 1. Turn continues until round
        // ends (nextPlayerIndex == 0).

        GameState state = new GameState(initialState.board(), List.of(p1, p2), 0, 1, false, null);

        // p1 takes tokens
        TakeTokensAction action = new TakeTokensAction(Map.of(Color.BLUE, 1, Color.GREEN, 1, Color.WHITE, 1), null);
        GameState nextState = engine.applyAction(state, action);

        assertFalse(nextState.isGameOver(), "Game should not be over until round ends");
        assertEquals(1, nextState.currentPlayerIndex());

        // p2 takes tokens (ends the round)
        GameState state2 = nextState;
        TakeTokensAction action2 = new TakeTokensAction(Map.of(Color.BLUE, 1, Color.GREEN, 1, Color.WHITE, 1), null);
        GameState finalState = engine.applyAction(state2, action2);

        assertTrue(finalState.isGameOver());
        assertTrue(finalState.winnerReason().contains("Player 0 won"));
    }
}
