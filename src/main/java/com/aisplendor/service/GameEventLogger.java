package com.aisplendor.service;

import com.aisplendor.model.event.GameEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Writes game events to an NDJSON (newline-delimited JSON) file.
 * Each event is written as a single JSON line for easy parsing.
 */
public class GameEventLogger implements Closeable {
    private static final Logger logger = LoggerFactory.getLogger(GameEventLogger.class);

    private final ObjectMapper objectMapper;
    private final BufferedWriter writer;
    private final Path logPath;

    /**
     * Creates a new GameEventLogger that writes to logs/{gameId}.json
     * 
     * @param gameId Unique identifier for this game session
     * @throws IOException if the log file cannot be created
     */
    public GameEventLogger(String gameId) throws IOException {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        Path logsDir = Path.of("logs");
        Files.createDirectories(logsDir);

        this.logPath = logsDir.resolve(gameId + ".json");
        this.writer = Files.newBufferedWriter(
                logPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);

        logger.info("Game event log created: {}", logPath);
    }

    /**
     * Logs a game event as a single JSON line.
     * 
     * @param event The event to log
     */
    public void log(GameEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            writer.write(json);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            logger.error("Failed to write game event: {}", e.getMessage());
        }
    }

    /**
     * @return The path to the log file
     */
    public Path getLogPath() {
        return logPath;
    }

    @Override
    public void close() throws IOException {
        if (writer != null) {
            writer.close();
            logger.info("Game event log closed: {}", logPath);
        }
    }
}
