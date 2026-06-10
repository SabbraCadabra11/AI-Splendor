package com.aisplendor.service;

import com.aisplendor.model.event.GameEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Publishes game events to registered listeners (e.g. WebSocket sessions).
 */
@Component
public class GameEventPublisher {
    private static final Logger logger = LoggerFactory.getLogger(GameEventPublisher.class);

    private final List<BiConsumer<String, GameEvent>> listeners = new CopyOnWriteArrayList<>();

    /**
     * Registers a listener to receive published events.
     *
     * @param listener BiConsumer taking gameId and GameEvent
     */
    public void registerListener(BiConsumer<String, GameEvent> listener) {
        listeners.add(listener);
        logger.debug("Registered a new game event listener. Total listeners: {}", listeners.size());
    }

    /**
     * Unregisters a listener.
     *
     * @param listener Listener to remove
     */
    public void unregisterListener(BiConsumer<String, GameEvent> listener) {
        listeners.remove(listener);
        logger.debug("Unregistered a game event listener. Total listeners: {}", listeners.size());
    }

    /**
     * Publishes an event to all registered listeners.
     *
     * @param gameId The game identifier this event belongs to
     * @param event The event being published
     */
    public void publish(String gameId, GameEvent event) {
        logger.debug("Publishing event {} for gameId: {}", event.eventType(), gameId);
        for (BiConsumer<String, GameEvent> listener : listeners) {
            try {
                listener.accept(gameId, event);
            } catch (Exception e) {
                logger.error("Error invoking listener for gameId: " + gameId, e);
            }
        }
    }
}
