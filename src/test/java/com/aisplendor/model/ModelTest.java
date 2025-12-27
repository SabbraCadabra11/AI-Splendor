package com.aisplendor.model;

import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ModelTest {

    @Test
    void testTokenBankCreation() {
        Map<Color, Integer> tokens = new HashMap<>();
        tokens.put(Color.RED, 5);
        TokenBank bank = new TokenBank(tokens);

        assertEquals(5, bank.getCount(Color.RED));
        assertEquals(0, bank.getCount(Color.BLUE));
        assertEquals(5, bank.totalTokens());
    }

    @Test
    void testGameStateCreation() {
        TokenBank emptyBank = new TokenBank(Collections.emptyMap());
        Board board = new Board(
                emptyBank,
                new HashMap<>(),
                new HashMap<>(),
                Collections.emptyList());

        Player player = new Player(
                0,
                emptyBank,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                0,
                Collections.emptyMap(),
                Collections.emptyList());

        GameState state = new GameState(
                board,
                List.of(player),
                0,
                1,
                false,
                null);

        assertNotNull(state);
        assertEquals(1, state.turnNumber());
    }
}
