package com.aisplendor.service;

import com.aisplendor.config.ReasoningConfig;
import com.aisplendor.model.CardLevel;
import com.aisplendor.model.Color;
import com.aisplendor.model.GameState;
import com.aisplendor.model.action.AgentResponse;
import com.aisplendor.model.action.GameAction;
import com.aisplendor.model.action.PurchaseCardAction;
import com.aisplendor.model.action.ReserveCardAction;
import com.aisplendor.model.action.TakeTokensAction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service to interact with OpenRouter API for LLM move generation.
 * Uses flattened JSON schema for reliable action parameter extraction.
 */
public class OpenRouterService {

    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private final String apiKey;
    private final String model;
    private final ReasoningConfig reasoningConfig;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public OpenRouterService(String apiKey, String model, ReasoningConfig reasoningConfig) {
        this.apiKey = apiKey;
        this.model = model;
        this.reasoningConfig = reasoningConfig;
        this.mapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public AgentResponse getNextMove(GameState state, String systemPrompt) throws Exception {
        String stateJson = mapper.writeValueAsString(state);

        // Flattened JSON schema - no nested action object
        Map<String, Object> jsonSchema = Map.of(
                "name", "game_action",
                "strict", true,
                "schema", Map.ofEntries(
                        Map.entry("type", "object"),
                        Map.entry("properties", Map.ofEntries(
                                Map.entry("reasoning", Map.of("type", "string")),
                                Map.entry("action_type", Map.of("type", "string", "enum",
                                        List.of("TAKE_TOKENS", "RESERVE_CARD", "PURCHASE_CARD"))),
                                // Flat token fields for TAKE_TOKENS
                                Map.entry("take_WHITE", Map.of("type", "integer")),
                                Map.entry("take_BLUE", Map.of("type", "integer")),
                                Map.entry("take_GREEN", Map.of("type", "integer")),
                                Map.entry("take_RED", Map.of("type", "integer")),
                                Map.entry("take_BLACK", Map.of("type", "integer")),
                                Map.entry("return_WHITE", Map.of("type", "integer")),
                                Map.entry("return_BLUE", Map.of("type", "integer")),
                                Map.entry("return_GREEN", Map.of("type", "integer")),
                                Map.entry("return_RED", Map.of("type", "integer")),
                                Map.entry("return_BLACK", Map.of("type", "integer")),
                                // For PURCHASE_CARD and RESERVE_CARD
                                Map.entry("card_id", Map.of("type", "string")),
                                // For RESERVE_CARD from deck
                                Map.entry("deck_level", Map.of("type", "string", "enum",
                                        List.of("", "LEVEL_1", "LEVEL_2", "LEVEL_3"))))),
                        Map.entry("required", List.of("reasoning", "action_type",
                                "take_WHITE", "take_BLUE", "take_GREEN", "take_RED", "take_BLACK",
                                "return_WHITE", "return_BLUE", "return_GREEN", "return_RED", "return_BLACK",
                                "card_id", "deck_level")),
                        Map.entry("additionalProperties", false)));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", "Current game state: " + stateJson)));
        requestBody.put("response_format", Map.of("type", "json_schema", "json_schema", jsonSchema));

        // Conditionally add reasoning configuration for models that support it
        if (reasoningConfig.enabled()) {
            requestBody.put("reasoning", Map.of(
                    "effort", reasoningConfig.effort(),
                    "exclude", reasoningConfig.exclude()));
        }

        String requestJson = mapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "API request failed with status " + response.statusCode() + ": "
                            + response.body());
        }

        return parseResponse(response.body());
    }

    private AgentResponse parseResponse(String responseBody) throws Exception {
        JsonNode root = mapper.readTree(responseBody);
        String content = root.path("choices").get(0).path("message").path("content").asText();

        // Sanitize any markdown code blocks
        content = sanitizeJson(content);

        JsonNode json = mapper.readTree(content);

        String reasoning = json.path("reasoning").asText("No reasoning provided.");
        String actionType = json.path("action_type").asText();

        GameAction action = switch (actionType) {
            case "TAKE_TOKENS" -> parseTakeTokensAction(json);
            case "PURCHASE_CARD" -> parsePurchaseCardAction(json);
            case "RESERVE_CARD" -> parseReserveCardAction(json);
            default -> throw new IllegalArgumentException("Unknown action type: " + actionType);
        };

        return new AgentResponse(reasoning, action);
    }

    private TakeTokensAction parseTakeTokensAction(JsonNode json) {
        Map<Color, Integer> tokens = new EnumMap<>(Color.class);
        Map<Color, Integer> tokensToReturn = new EnumMap<>(Color.class);

        addTokenIfPositive(tokens, json, "take_WHITE", Color.WHITE);
        addTokenIfPositive(tokens, json, "take_BLUE", Color.BLUE);
        addTokenIfPositive(tokens, json, "take_GREEN", Color.GREEN);
        addTokenIfPositive(tokens, json, "take_RED", Color.RED);
        addTokenIfPositive(tokens, json, "take_BLACK", Color.BLACK);

        addTokenIfPositive(tokensToReturn, json, "return_WHITE", Color.WHITE);
        addTokenIfPositive(tokensToReturn, json, "return_BLUE", Color.BLUE);
        addTokenIfPositive(tokensToReturn, json, "return_GREEN", Color.GREEN);
        addTokenIfPositive(tokensToReturn, json, "return_RED", Color.RED);
        addTokenIfPositive(tokensToReturn, json, "return_BLACK", Color.BLACK);

        return new TakeTokensAction(
                tokens.isEmpty() ? null : tokens,
                tokensToReturn.isEmpty() ? null : tokensToReturn);
    }

    private void addTokenIfPositive(Map<Color, Integer> map, JsonNode json, String field, Color color) {
        int value = json.path(field).asInt(0);
        if (value > 0) {
            map.put(color, value);
        }
    }

    private PurchaseCardAction parsePurchaseCardAction(JsonNode json) {
        String cardId = json.path("card_id").asText(null);
        if (cardId != null && cardId.isEmpty()) {
            cardId = null;
        }
        return new PurchaseCardAction(cardId);
    }

    private ReserveCardAction parseReserveCardAction(JsonNode json) {
        String cardId = json.path("card_id").asText(null);
        if (cardId != null && cardId.isEmpty()) {
            cardId = null;
        }

        String deckLevelStr = json.path("deck_level").asText(null);
        CardLevel deckLevel = null;
        if (deckLevelStr != null && !deckLevelStr.isEmpty()) {
            deckLevel = CardLevel.valueOf(deckLevelStr);
        }

        Map<Color, Integer> tokensToReturn = new EnumMap<>(Color.class);
        addTokenIfPositive(tokensToReturn, json, "return_WHITE", Color.WHITE);
        addTokenIfPositive(tokensToReturn, json, "return_BLUE", Color.BLUE);
        addTokenIfPositive(tokensToReturn, json, "return_GREEN", Color.GREEN);
        addTokenIfPositive(tokensToReturn, json, "return_RED", Color.RED);
        addTokenIfPositive(tokensToReturn, json, "return_BLACK", Color.BLACK);

        return new ReserveCardAction(
                cardId,
                deckLevel,
                tokensToReturn.isEmpty() ? null : tokensToReturn);
    }

    private String sanitizeJson(String content) {
        if (content.startsWith("```json")) {
            content = content.substring(7);
        }
        if (content.startsWith("```")) {
            content = content.substring(3);
        }
        if (content.endsWith("```")) {
            content = content.substring(0, content.length() - 3);
        }
        return content.trim();
    }
}
