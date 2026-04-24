package com.aisplendor.service;

import com.aisplendor.config.DynamicReasoningConfig;
import com.aisplendor.config.ReasoningConfig;
import com.aisplendor.model.CardLevel;
import com.aisplendor.model.Color;
import com.aisplendor.model.GameState;
import com.aisplendor.model.action.AgentResponse;
import com.aisplendor.model.action.GameAction;
import com.aisplendor.model.action.PurchaseCardAction;
import com.aisplendor.model.action.ReserveCardAction;
import com.aisplendor.model.action.TakeTokensAction;
import com.aisplendor.util.CompactStateSerializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service to interact with OpenRouter API for LLM move generation.
 * Uses flattened JSON schema for reliable action parameter extraction.
 * Supports dynamic reasoning effort, prompt caching, and conditional
 * reasoning field in the output schema.
 */
public class OpenRouterService {
    private static final Logger logger = LoggerFactory.getLogger(OpenRouterService.class);

    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private final String apiKey;
    private final String model;
    private final DynamicReasoningConfig dynamicReasoningConfig;
    private final boolean debugMode;
    private final String promptCachingSetting;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public OpenRouterService(String apiKey, String model, DynamicReasoningConfig dynamicReasoningConfig,
            boolean debugMode, String promptCachingSetting) {
        this.apiKey = apiKey;
        this.model = model;
        this.dynamicReasoningConfig = dynamicReasoningConfig;
        this.debugMode = debugMode;
        this.promptCachingSetting = promptCachingSetting;
        this.mapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Constructor without prompt caching setting (defaults to "auto").
     */
    public OpenRouterService(String apiKey, String model, DynamicReasoningConfig dynamicReasoningConfig,
            boolean debugMode) {
        this(apiKey, model, dynamicReasoningConfig, debugMode, "auto");
    }

    /**
     * Backward-compatible constructor that wraps a static ReasoningConfig.
     */
    public OpenRouterService(String apiKey, String model, ReasoningConfig reasoningConfig, boolean debugMode) {
        this(apiKey, model, DynamicReasoningConfig.fromStatic(reasoningConfig), debugMode, "auto");
    }

    /**
     * Get the next move from the LLM.
     *
     * @param state        The current game state
     * @param systemPrompt The system prompt (rules + memory)
     * @param retryContext Optional retry context to append to the user message (null for first attempt)
     * @return The agent's response with reasoning and action
     */
    public AgentResponse getNextMove(GameState state, String systemPrompt, String retryContext) throws Exception {
        // Convert to compact text format for LLM consumption
        String compactState = CompactStateSerializer.serialize(state, state.currentPlayerIndex());

        // Build user message — compact state + optional retry context
        String userContent;
        if (retryContext != null && !retryContext.isBlank()) {
            userContent = compactState + "\n\n" + retryContext;
        } else {
            userContent = compactState;
        }

        // Resolve reasoning effort for this turn (dynamic or static)
        int turnNumber = state.turnNumber();
        ReasoningConfig resolvedReasoning = dynamicReasoningConfig.resolveForTurn(turnNumber);

        // Build JSON schema — conditionally include/exclude "reasoning" field
        Map<String, Object> jsonSchema = buildJsonSchema(resolvedReasoning.enabled());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);

        // Build messages — use array content format for Anthropic prompt caching
        List<Object> messages = buildMessages(systemPrompt, userContent);
        requestBody.put("messages", messages);

        requestBody.put("response_format", Map.of("type", "json_schema", "json_schema", jsonSchema));

        // Add reasoning configuration
        if (resolvedReasoning.enabled()) {
            requestBody.put("reasoning", Map.of(
                    "effort", resolvedReasoning.effort(),
                    "exclude", resolvedReasoning.exclude()));
        }

        String requestJson = mapper.writeValueAsString(requestBody);

        if (debugMode) {
            logger.info("[DEBUG] OpenRouter Request Body:\n{}", requestJson);
            if (dynamicReasoningConfig.isDynamic() && resolvedReasoning.enabled()) {
                logger.info("[DEBUG] Dynamic reasoning effort for turn {}: {}", turnNumber,
                        resolvedReasoning.effort());
            }
        }

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

        return parseResponse(response.body(), resolvedReasoning.enabled());
    }

    /**
     * Backward-compatible overload without retry context.
     */
    public AgentResponse getNextMove(GameState state, String systemPrompt) throws Exception {
        return getNextMove(state, systemPrompt, null);
    }

    /**
     * Builds the JSON schema for the action response.
     * When API reasoning is enabled, the "reasoning" field is excluded from the schema
     * to save output tokens — the model's internal chain-of-thought handles strategic analysis.
     *
     * @param apiReasoningEnabled Whether reasoning is enabled via the API parameter for this turn
     * @return The JSON schema map
     */
    private Map<String, Object> buildJsonSchema(boolean apiReasoningEnabled) {
        // Build properties map
        Map<String, Object> properties = new LinkedHashMap<>();

        if (!apiReasoningEnabled) {
            // When API reasoning is disabled, require the reasoning field as the only
            // source of strategic analysis
            properties.put("reasoning", Map.of("type", "string"));
        }

        properties.put("action_type", Map.of("type", "string", "enum",
                List.of("TAKE_TOKENS", "RESERVE_CARD", "PURCHASE_CARD")));
        // Flat token fields for TAKE_TOKENS
        properties.put("take_WHITE", Map.of("type", "integer"));
        properties.put("take_BLUE", Map.of("type", "integer"));
        properties.put("take_GREEN", Map.of("type", "integer"));
        properties.put("take_RED", Map.of("type", "integer"));
        properties.put("take_BLACK", Map.of("type", "integer"));
        properties.put("return_WHITE", Map.of("type", "integer"));
        properties.put("return_BLUE", Map.of("type", "integer"));
        properties.put("return_GREEN", Map.of("type", "integer"));
        properties.put("return_RED", Map.of("type", "integer"));
        properties.put("return_BLACK", Map.of("type", "integer"));
        // For PURCHASE_CARD and RESERVE_CARD
        properties.put("card_id", Map.of("type", "string"));
        // For RESERVE_CARD from deck
        properties.put("deck_level", Map.of("type", "string", "enum",
                List.of("", "LEVEL_1", "LEVEL_2", "LEVEL_3")));

        // Build required fields list
        List<String> required = new ArrayList<>();
        if (!apiReasoningEnabled) {
            required.add("reasoning");
        }
        required.addAll(List.of("action_type",
                "take_WHITE", "take_BLUE", "take_GREEN", "take_RED", "take_BLACK",
                "return_WHITE", "return_BLUE", "return_GREEN", "return_RED", "return_BLACK",
                "card_id", "deck_level"));

        return Map.of(
                "name", "game_action",
                "strict", true,
                "schema", Map.ofEntries(
                        Map.entry("type", "object"),
                        Map.entry("properties", properties),
                        Map.entry("required", required),
                        Map.entry("additionalProperties", false)));
    }

    /**
     * Builds the messages list. For Anthropic models with prompt caching enabled,
     * uses the array content format with cache_control on the system message.
     * For other models, uses the standard string content format.
     */
    private List<Object> buildMessages(String systemPrompt, String userContent) {
        boolean usePromptCaching = shouldUsePromptCaching();

        List<Object> messages = new ArrayList<>();

        if (usePromptCaching) {
            // Array content format with cache_control for Anthropic models
            Map<String, Object> cacheBlock = new LinkedHashMap<>();
            cacheBlock.put("type", "text");
            cacheBlock.put("text", systemPrompt);
            cacheBlock.put("cache_control", Map.of("type", "ephemeral"));

            messages.add(Map.of("role", "system", "content", List.of(cacheBlock)));
        } else {
            // Standard string content format
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }

        messages.add(Map.of("role", "user", "content", userContent));
        return messages;
    }

    /**
     * Determines whether to use prompt caching based on the configured setting
     * and the model string.
     */
    private boolean shouldUsePromptCaching() {
        return switch (promptCachingSetting) {
            case "true" -> true;
            case "false" -> false;
            default -> model.startsWith("anthropic/"); // "auto" — detect by model prefix
        };
    }

    /**
     * Parses the LLM response. When API reasoning was enabled and the "reasoning" field
     * is absent from the JSON content, attempts to extract reasoning from the API
     * response's reasoning_content field (if exclude=false). Falls back to a default message.
     */
    private AgentResponse parseResponse(String responseBody, boolean apiReasoningEnabled) throws Exception {
        JsonNode root = mapper.readTree(responseBody);
        JsonNode message = root.path("choices").get(0).path("message");
        String content = message.path("content").asText();

        // Sanitize any markdown code blocks
        content = sanitizeJson(content);

        JsonNode json = mapper.readTree(content);

        // Determine reasoning text
        String reasoning;
        if (apiReasoningEnabled) {
            // Try to extract from API reasoning_content (available when exclude=false)
            String apiReasoning = message.path("reasoning_content").asText(null);
            if (apiReasoning != null && !apiReasoning.isBlank()) {
                reasoning = apiReasoning;
            } else {
                // Reasoning was internal-only (exclude=true) or not available
                reasoning = json.path("reasoning").asText("(API reasoning enabled — reasoning handled internally)");
            }
        } else {
            // Reasoning from the JSON schema field
            reasoning = json.path("reasoning").asText("No reasoning provided.");
        }

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
