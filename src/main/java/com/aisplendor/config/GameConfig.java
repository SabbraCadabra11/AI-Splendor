package com.aisplendor.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class GameConfig {
    private final Properties properties = new Properties();

    /**
     * Load configuration from the default classpath resource
     * (application.properties).
     */
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

    /**
     * Load configuration from an external file path.
     * 
     * @param propertiesFile Path to the properties file
     */
    public GameConfig(Path propertiesFile) {
        if (propertiesFile == null || !Files.exists(propertiesFile)) {
            System.err.println("Warning: Properties file not found: " + propertiesFile + ", using defaults.");
            return;
        }
        try (InputStream input = new FileInputStream(propertiesFile.toFile())) {
            properties.load(input);
        } catch (IOException ex) {
            System.err.println("Error loading properties file: " + propertiesFile);
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
     * Get the prompt caching setting.
     * Values: "auto" (detect by model prefix), "true" (always), "false" (never).
     * Default: "auto"
     */
    public String getPromptCachingSetting() {
        return properties.getProperty("game.prompt-caching", "auto").trim().toLowerCase();
    }

    /**
     * Get the reasoning memory window size for a specific player.
     * This controls how many previous reasoning summaries are included in the prompt.
     *
     * @param playerIndex The player index (0 or 1)
     * @return The memory size (minimum 2, default 3)
     */
    public int getPlayerMemorySize(int playerIndex) {
        String value = properties.getProperty("player" + playerIndex + ".memory.size", "3");
        int size = Integer.parseInt(value.trim());
        return Math.max(2, size);
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

    /**
     * Get dynamic reasoning configuration for a specific player.
     * Reads from player{N}.reasoning.dynamic and player{N}.reasoning.phases.
     *
     * Example properties:
     * player0.reasoning.dynamic=true
     * player0.reasoning.phases=1-5:medium,6+:high
     *
     * When dynamic is false (default), the static reasoning config is used for all turns.
     *
     * @param playerIndex The player index (0 or 1)
     * @return DynamicReasoningConfig wrapping the static config with optional phase rules
     */
    public DynamicReasoningConfig getDynamicReasoningConfig(int playerIndex) {
        ReasoningConfig staticConfig = getPlayerReasoning(playerIndex);
        String prefix = "player" + playerIndex + ".reasoning.";
        boolean dynamic = Boolean.parseBoolean(properties.getProperty(prefix + "dynamic", "false"));
        String phases = properties.getProperty(prefix + "phases");
        return new DynamicReasoningConfig(dynamic, phases, staticConfig);
    }

    /**
     * Get knockout stage configuration.
     * Reads from game.stage, game.leg, game.firstLegResult,
     * game.firstLegCardsBought
     * 
     * Example properties:
     * game.stage=quarter-final
     * game.leg=2
     * game.firstLegResult=12:15
     * game.firstLegCardsBought=15:11
     * 
     * @return StageConfig with stage info, or StageConfig.none() if not configured
     */
    public StageConfig getStageConfig() {
        String stage = properties.getProperty("game.stage");
        if (stage == null || stage.isBlank()) {
            return StageConfig.none();
        }

        int leg = Integer.parseInt(properties.getProperty("game.leg", "1"));

        int player0Score = 0;
        int player1Score = 0;
        int player0Cards = 0;
        int player1Cards = 0;

        if (leg == 2) {
            String firstLegResult = properties.getProperty("game.firstLegResult", "0:0");
            String[] scores = firstLegResult.split(":");
            if (scores.length == 2) {
                player0Score = Integer.parseInt(scores[0].trim());
                player1Score = Integer.parseInt(scores[1].trim());
            }

            String firstLegCards = properties.getProperty("game.firstLegCardsBought", "0:0");
            String[] cards = firstLegCards.split(":");
            if (cards.length == 2) {
                player0Cards = Integer.parseInt(cards[0].trim());
                player1Cards = Integer.parseInt(cards[1].trim());
            }
        }

        boolean swappedStartingPlayer = Boolean.parseBoolean(
                properties.getProperty("game.swappedStartingPlayer", "false"));

        return new StageConfig(stage, leg, player0Score, player1Score, player0Cards, player1Cards,
                swappedStartingPlayer);
    }
}
