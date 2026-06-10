package com.aisplendor.service;

import com.aisplendor.model.event.GameEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(GameWebSocketHandler.class);

    private final GameEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    @Autowired
    public GameWebSocketHandler(GameEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @PostConstruct
    public void init() {
        eventPublisher.registerListener(this::handleGameEvent);
        logger.info("GameWebSocketHandler initialized and registered to GameEventPublisher");
    }

    @PreDestroy
    public void cleanup() {
        eventPublisher.unregisterListener(this::handleGameEvent);
        logger.info("GameWebSocketHandler unregistered from GameEventPublisher");
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String gameId = getGameId(session);
        if (gameId != null) {
            sessions.computeIfAbsent(gameId, k -> new CopyOnWriteArraySet<>()).add(session);
            logger.info("WebSocket connection established for gameId: {}. Active sessions for this game: {}", 
                    gameId, sessions.get(gameId).size());
        } else {
            logger.warn("WebSocket connection established but no gameId found in URI: {}", session.getUri());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String gameId = getGameId(session);
        if (gameId != null) {
            Set<WebSocketSession> gameSessions = sessions.get(gameId);
            if (gameSessions != null) {
                gameSessions.remove(session);
                if (gameSessions.isEmpty()) {
                    sessions.remove(gameId);
                }
                logger.info("WebSocket connection closed for gameId: {}. Remaining active sessions: {}", 
                        gameId, gameSessions.isEmpty() ? 0 : gameSessions.size());
            }
        }
    }

    private void handleGameEvent(String gameId, GameEvent event) {
        Set<WebSocketSession> gameSessions = sessions.get(gameId);
        if (gameSessions == null || gameSessions.isEmpty()) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(event);
            TextMessage message = new TextMessage(json);
            
            logger.debug("Broadcasting event {} to {} sessions of game {}", 
                    event.eventType(), gameSessions.size(), gameId);

            for (WebSocketSession session : gameSessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(message);
                    } catch (IOException e) {
                        logger.error("Failed to send WebSocket message to session " + session.getId(), e);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to serialize game event for WebSocket broadcast", e);
        }
    }

    private String getGameId(WebSocketSession session) {
        if (session.getUri() == null) {
            return null;
        }
        String path = session.getUri().getPath();
        // Path matches: /ws/game/{gameId}
        int index = path.lastIndexOf('/');
        if (index != -1 && index < path.length() - 1) {
            return path.substring(index + 1);
        }
        return null;
    }
}
