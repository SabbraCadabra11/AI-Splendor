package com.aisplendor.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class GameConfig {
    private final Properties properties = new Properties();

    public GameConfig() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) {
                System.err.println("Warning: application.properties not found, using defaults.");
                return;
            }
            properties.load(input);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public String getPlayer0Model() {
        return properties.getProperty("player0.model", "google/gemini-3-flash-preview");
    }

    public String getPlayer1Model() {
        return properties.getProperty("player1.model", "anthropic/claude-haiku-4.5");
    }

    public boolean isSemiAuto() {
        return Boolean.parseBoolean(properties.getProperty("game.semi-auto", "false"));
    }

    public boolean isDebugMode() {
        return Boolean.parseBoolean(properties.getProperty("game.debug-mode", "false"));
    }

    /**
     * Get reasoning configuration for a specific player.
     * Reads from player{N}.reasoning.enabled, player{N}.reasoning.effort,
     * player{N}.reasoning.exclude
     */
    public ReasoningConfig getPlayerReasoning(int playerIndex) {
        String prefix = "player" + playerIndex + ".reasoning.";
        boolean enabled = Boolean.parseBoolean(properties.getProperty(prefix + "enabled", "false"));
        if (!enabled) {
            return ReasoningConfig.disabled();
        }
        String effort = properties.getProperty(prefix + "effort", "medium");
        boolean exclude = Boolean.parseBoolean(properties.getProperty(prefix + "exclude", "true"));
        return new ReasoningConfig(enabled, effort, exclude);
    }
}
