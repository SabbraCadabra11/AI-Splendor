package com.aisplendor.service;

import com.aisplendor.config.DynamicReasoningConfig;
import com.aisplendor.config.GameConfig;
import com.aisplendor.config.StageConfig;
import com.aisplendor.engine.GameSimulator;
import com.aisplendor.model.GameState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class MatchManagerService {
    private static final Logger logger = LoggerFactory.getLogger(MatchManagerService.class);

    private final GameEventPublisher eventPublisher;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final Map<String, MatchInfo> matches = new ConcurrentHashMap<>();

    @Autowired
    public MatchManagerService(GameEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public synchronized String startMatch(String player0Model, String player1Model,
                                         DynamicReasoningConfig reasoning0, DynamicReasoningConfig reasoning1,
                                         int memory0, int memory1, boolean debugMode,
                                         String apiKeyOverride, String promptCachingSetting,
                                         StageConfig stageConfig, String player0Name, String player1Name,
                                         double player0InputCost, double player0OutputCost,
                                         double player1InputCost, double player1OutputCost) {
        String gameId = "game_" + System.currentTimeMillis();
        
        String apiKey = (apiKeyOverride != null && !apiKeyOverride.isBlank()) 
                ? apiKeyOverride 
                : System.getenv("OPENROUTER_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API Key is not configured (neither via environment nor override)");
        }

        String displayName0 = (player0Name != null && !player0Name.isBlank()) ? player0Name : player0Model;
        String displayName1 = (player1Name != null && !player1Name.isBlank()) ? player1Name : player1Model;

        MatchInfo info = new MatchInfo(gameId, displayName0, displayName1, "RUNNING", Instant.now());
        matches.put(gameId, info);

        executorService.submit(() -> {
            try {
                logger.info("Starting simulation match: {}", gameId);
                GameSimulator simulator = new GameSimulator(
                        apiKey, player0Model, player1Model,
                        reasoning0, reasoning1,
                        false, debugMode,
                        stageConfig, memory0, memory1,
                        promptCachingSetting,
                        player0InputCost, player0OutputCost,
                        player1InputCost, player1OutputCost,
                        eventPublisher
                );
                
                GameState initialState = GameSimulator.setupInitialState();
                simulator.run(initialState, gameId, displayName0, displayName1);
                
                info.setStatus("COMPLETED");
                logger.info("Match {} completed successfully", gameId);
            } catch (Exception e) {
                info.setStatus("FAILED");
                logger.error("Match " + gameId + " failed with error: ", e);
            }
        });

        return gameId;
    }

    public synchronized String resumeMatch(String logFileName, String apiKeyOverride) {
        // Find log file
        Path logFile = Path.of("logs").resolve(logFileName);
        if (!Files.exists(logFile)) {
            throw new IllegalArgumentException("Log file not found: " + logFileName);
        }

        String apiKey = (apiKeyOverride != null && !apiKeyOverride.isBlank())
                ? apiKeyOverride
                : System.getenv("OPENROUTER_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API Key is not configured");
        }

        try {
            GameLogReader reader = new GameLogReader();
            GameLogReader.ResumeData resumeData = reader.parseLogForResume(logFile);
            
            String newGameId = resumeData.originalGameId() + "_resumed_" + System.currentTimeMillis();
            
            MatchInfo info = new MatchInfo(
                    newGameId, 
                    resumeData.player0Name(), 
                    resumeData.player1Name(), 
                    "RUNNING", 
                    Instant.now()
            );
            matches.put(newGameId, info);

            executorService.submit(() -> {
                try {
                    logger.info("Resuming simulation match {} -> {}", resumeData.originalGameId(), newGameId);
                    
                    // Standard config values as per GameSimulator.resumeGame
                    GameConfig config = new GameConfig();
                    DynamicReasoningConfig dynamicReasoning0 = config.getDynamicReasoningConfig(0);
                    DynamicReasoningConfig dynamicReasoning1 = config.getDynamicReasoningConfig(1);
                    boolean debugMode = config.isDebugMode();
                    int memorySize0 = config.getPlayerMemorySize(0);
                    int memorySize1 = config.getPlayerMemorySize(1);
                    String promptCachingSetting = config.getPromptCachingSetting();

                    GameSimulator simulator = new GameSimulator(
                            apiKey,
                            resumeData.player0Model(),
                            resumeData.player1Model(),
                            dynamicReasoning0,
                            dynamicReasoning1,
                            false,
                            debugMode,
                            StageConfig.none(),
                            memorySize0,
                            memorySize1,
                            promptCachingSetting,
                            resumeData.player0InputCost(),
                            resumeData.player0OutputCost(),
                            resumeData.player1InputCost(),
                            resumeData.player1OutputCost(),
                            eventPublisher
                    );

                    simulator.run(
                            resumeData.resumeState(), 
                            newGameId,
                            resumeData.player0Name(), 
                            resumeData.player1Name(),
                            resumeData.player0AccumulatedTimeMs(), 
                            resumeData.player1AccumulatedTimeMs()
                    );

                    info.setStatus("COMPLETED");
                    logger.info("Resumed match {} completed successfully", newGameId);
                } catch (Exception e) {
                    info.setStatus("FAILED");
                    logger.error("Resumed match " + newGameId + " failed with error: ", e);
                }
            });

            return newGameId;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read log file for resume: " + logFileName, e);
        }
    }

    public List<MatchInfo> getMatches() {
        return new ArrayList<>(matches.values());
    }

    public List<String> getLogs() {
        Path logsDir = Path.of("logs");
        if (!Files.exists(logsDir)) {
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.list(logsDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.endsWith(".json"))
                    .sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("Failed to list log files", e);
            return Collections.emptyList();
        }
    }

    public static class MatchInfo {
        private final String gameId;
        private final String player0Model;
        private final String player1Model;
        private String status;
        private final Instant startTime;

        public MatchInfo(String gameId, String player0Model, String player1Model, String status, Instant startTime) {
            this.gameId = gameId;
            this.player0Model = player0Model;
            this.player1Model = player1Model;
            this.status = status;
            this.startTime = startTime;
        }

        public String getGameId() { return gameId; }
        public String getPlayer0Model() { return player0Model; }
        public String getPlayer1Model() { return player1Model; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Instant getStartTime() { return startTime; }
    }
}
