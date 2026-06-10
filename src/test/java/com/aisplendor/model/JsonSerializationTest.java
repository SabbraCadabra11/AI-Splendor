package com.aisplendor.model;

import com.aisplendor.model.action.AgentResponse;
import com.aisplendor.model.action.GameAction;
import com.aisplendor.model.action.PurchaseCardAction;
import com.aisplendor.model.action.ReserveCardAction;
import com.aisplendor.model.action.TakeTokensAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testGameActionSerialization() throws Exception {
        TakeTokensAction take = new TakeTokensAction(Map.of(Color.RED, 1), null);
        String json = mapper.writeValueAsString(take);
        assertTrue(json.contains("\"type\":\"TAKE_TOKENS\""));

        GameAction deserialized = mapper.readValue(json, GameAction.class);
        assertTrue(deserialized instanceof TakeTokensAction);
        assertEquals(1, ((TakeTokensAction) deserialized).tokens().get(Color.RED));
    }

    @Test
    void testReserveCardSerialization() throws Exception {
        ReserveCardAction reserve = new ReserveCardAction("c1", null, Map.of(Color.BLUE, 1));
        String json = mapper.writeValueAsString(reserve);
        assertTrue(json.contains("\"type\":\"RESERVE_CARD\""));

        GameAction deserialized = mapper.readValue(json, GameAction.class);
        assertTrue(deserialized instanceof ReserveCardAction);
        assertEquals("c1", ((ReserveCardAction) deserialized).cardId());
    }

    @Test
    void testPurchaseCardSerialization() throws Exception {
        PurchaseCardAction purchase = new PurchaseCardAction("c2");
        String json = mapper.writeValueAsString(purchase);
        assertTrue(json.contains("\"type\":\"PURCHASE_CARD\""));

        GameAction deserialized = mapper.readValue(json, GameAction.class);
        assertTrue(deserialized instanceof PurchaseCardAction);
        assertEquals("c2", ((PurchaseCardAction) deserialized).cardId());
    }

    @Test
    void testAgentResponseSerialization() throws Exception {
        AgentResponse response = new AgentResponse("I need RED tokens.",
                new TakeTokensAction(Map.of(Color.RED, 1), null), TokenUsage.zero());
        String json = mapper.writeValueAsString(response);
        assertTrue(json.contains("\"reasoning\":\"I need RED tokens.\""));
        assertTrue(json.contains("\"type\":\"TAKE_TOKENS\""));

        AgentResponse deserialized = mapper.readValue(json, AgentResponse.class);
        assertEquals("I need RED tokens.", deserialized.reasoning());
        assertTrue(deserialized.action() instanceof TakeTokensAction);
    }

    @Test
    void testActionEventSerialization() throws Exception {
        ObjectMapper mapperWithTime = new ObjectMapper();
        mapperWithTime.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

        com.aisplendor.model.event.ActionEvent event = new com.aisplendor.model.event.ActionEvent(
                java.time.Instant.parse("2025-01-01T00:00:00Z"),
                0,
                new PurchaseCardAction("c2"),
                true,
                1500L
        );
        String json = mapperWithTime.writeValueAsString(event);
        assertTrue(json.contains("\"durationMs\":1500"));
        assertTrue(json.contains("\"playerIndex\":0"));
        assertTrue(json.contains("\"success\":true"));

        com.aisplendor.model.event.ActionEvent deserialized = mapperWithTime.readValue(json, com.aisplendor.model.event.ActionEvent.class);
        assertEquals(1500L, deserialized.durationMs());
        assertEquals(0, deserialized.playerIndex());
        assertTrue(deserialized.success());
    }
}
