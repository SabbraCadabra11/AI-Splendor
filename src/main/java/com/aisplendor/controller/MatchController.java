package com.aisplendor.controller;

import com.aisplendor.config.DynamicReasoningConfig;
import com.aisplendor.config.ReasoningConfig;
import com.aisplendor.config.StageConfig;
import com.aisplendor.model.dto.GameConfigRequest;
import com.aisplendor.service.MatchManagerService;
import com.aisplendor.service.MatchManagerService.MatchInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class MatchController {
    private static final Logger logger = LoggerFactory.getLogger(MatchController.class);

    private final MatchManagerService matchManagerService;

    @Autowired
    public MatchController(MatchManagerService matchManagerService) {
        this.matchManagerService = matchManagerService;
    }

    @PostMapping("/matches/start")
    public ResponseEntity<?> startMatch(@RequestBody GameConfigRequest request) {
        try {
            logger.info("REST request to start match. P0: {}, P1: {}", 
                    request.getPlayer0Model(), request.getPlayer1Model());
            
            ReasoningConfig r0 = request.isPlayer0ReasoningEnabled()
                    ? new ReasoningConfig(true, request.getPlayer0ReasoningEffort(), request.isPlayer0ReasoningExclude())
                    : ReasoningConfig.disabled();
            DynamicReasoningConfig dynamicR0 = new DynamicReasoningConfig(
                    request.isPlayer0ReasoningDynamic(), request.getPlayer0ReasoningPhases(), r0);

            ReasoningConfig r1 = request.isPlayer1ReasoningEnabled()
                    ? new ReasoningConfig(true, request.getPlayer1ReasoningEffort(), request.isPlayer1ReasoningExclude())
                    : ReasoningConfig.disabled();
            DynamicReasoningConfig dynamicR1 = new DynamicReasoningConfig(
                    request.isPlayer1ReasoningDynamic(), request.getPlayer1ReasoningPhases(), r1);

            StageConfig stageConfig = request.isStageEnabled()
                    ? new StageConfig(
                            request.getStageName(),
                            request.getStageLeg(),
                            request.getStageFirstLegResultP0(),
                            request.getStageFirstLegResultP1(),
                            request.getStageFirstLegCardsP0(),
                            request.getStageFirstLegCardsP1(),
                            request.isStageSwappedStartingPlayer())
                    : StageConfig.none();

            String gameId = matchManagerService.startMatch(
                    request.getPlayer0Model(),
                    request.getPlayer1Model(),
                    dynamicR0,
                    dynamicR1,
                    request.getPlayer0MemorySize(),
                    request.getPlayer1MemorySize(),
                    request.isDebugMode(),
                    request.getApiKeyOverride(),
                    request.getPromptCachingSetting(),
                    stageConfig,
                    request.getPlayer0Name(),
                    request.getPlayer1Name(),
                    request.getPlayer0InputTokenCost(),
                    request.getPlayer0OutputTokenCost(),
                    request.getPlayer1InputTokenCost(),
                    request.getPlayer1OutputTokenCost()
            );

            return ResponseEntity.ok(Map.of("gameId", gameId));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid configuration for starting match", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to start match", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }

    @PostMapping("/matches/resume")
    public ResponseEntity<?> resumeMatch(@RequestBody Map<String, String> payload) {
        try {
            String logFileName = payload.get("logFileName");
            String apiKeyOverride = payload.get("apiKeyOverride");
            
            if (logFileName == null || logFileName.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "logFileName is required"));
            }

            logger.info("REST request to resume match from log: {}", logFileName);
            String gameId = matchManagerService.resumeMatch(logFileName, apiKeyOverride);
            return ResponseEntity.ok(Map.of("gameId", gameId));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid state for resuming match", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to resume match", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }

    @GetMapping("/matches")
    public ResponseEntity<List<MatchInfo>> getMatches() {
        return ResponseEntity.ok(matchManagerService.getMatches());
    }

    @GetMapping("/logs")
    public ResponseEntity<List<String>> getLogs() {
        return ResponseEntity.ok(matchManagerService.getLogs());
    }

    @GetMapping("/logs/{filename:.+}")
    public ResponseEntity<?> getLogContent(@PathVariable String filename) {
        try {
            // Strip any path traversal elements
            String cleanName = Path.of(filename).getFileName().toString();
            Path logPath = Path.of("logs").resolve(cleanName);
            
            if (!Files.exists(logPath)) {
                return ResponseEntity.notFound().build();
            }

            byte[] content = Files.readAllBytes(logPath);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + cleanName + "\"")
                    .body(content);
        } catch (IOException e) {
            logger.error("Failed to read log file content: " + filename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to read log file: " + e.getMessage()));
        }
    }

    @PostMapping("/config/export")
    public ResponseEntity<byte[]> exportConfig(@RequestBody GameConfigRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("# AI-Splendor Game Configuration Properties\n");
        sb.append("# Generated on ").append(java.time.LocalDateTime.now()).append("\n\n");

        sb.append("# Player 0 Configuration\n");
        sb.append("player0.model=").append(request.getPlayer0Model()).append("\n");
        if (request.getPlayer0Name() != null && !request.getPlayer0Name().isBlank()) {
            sb.append("player0.name=").append(request.getPlayer0Name()).append("\n");
        }
        sb.append("player0.memory.size=").append(request.getPlayer0MemorySize()).append("\n");
        sb.append("player0.input.cost=").append(request.getPlayer0InputTokenCost()).append("\n");
        sb.append("player0.output.cost=").append(request.getPlayer0OutputTokenCost()).append("\n");
        sb.append("player0.reasoning.enabled=").append(request.isPlayer0ReasoningEnabled()).append("\n");
        if (request.isPlayer0ReasoningEnabled()) {
            sb.append("player0.reasoning.effort=").append(request.getPlayer0ReasoningEffort()).append("\n");
            sb.append("player0.reasoning.exclude=").append(request.isPlayer0ReasoningExclude()).append("\n");
            sb.append("player0.reasoning.dynamic=").append(request.isPlayer0ReasoningDynamic()).append("\n");
            if (request.isPlayer0ReasoningDynamic() && request.getPlayer0ReasoningPhases() != null) {
                sb.append("player0.reasoning.phases=").append(request.getPlayer0ReasoningPhases()).append("\n");
            }
        }
        sb.append("\n");

        sb.append("# Player 1 Configuration\n");
        sb.append("player1.model=").append(request.getPlayer1Model()).append("\n");
        if (request.getPlayer1Name() != null && !request.getPlayer1Name().isBlank()) {
            sb.append("player1.name=").append(request.getPlayer1Name()).append("\n");
        }
        sb.append("player1.memory.size=").append(request.getPlayer1MemorySize()).append("\n");
        sb.append("player1.input.cost=").append(request.getPlayer1InputTokenCost()).append("\n");
        sb.append("player1.output.cost=").append(request.getPlayer1OutputTokenCost()).append("\n");
        sb.append("player1.reasoning.enabled=").append(request.isPlayer1ReasoningEnabled()).append("\n");
        if (request.isPlayer1ReasoningEnabled()) {
            sb.append("player1.reasoning.effort=").append(request.getPlayer1ReasoningEffort()).append("\n");
            sb.append("player1.reasoning.exclude=").append(request.isPlayer1ReasoningExclude()).append("\n");
            sb.append("player1.reasoning.dynamic=").append(request.isPlayer1ReasoningDynamic()).append("\n");
            if (request.isPlayer1ReasoningDynamic() && request.getPlayer1ReasoningPhases() != null) {
                sb.append("player1.reasoning.phases=").append(request.getPlayer1ReasoningPhases()).append("\n");
            }
        }
        sb.append("\n");

        sb.append("# Global Game Settings\n");
        sb.append("game.semi-auto=false\n");
        sb.append("game.debug-mode=").append(request.isDebugMode()).append("\n");
        sb.append("game.prompt-caching=").append(request.getPromptCachingSetting()).append("\n");

        if (request.isStageEnabled()) {
            sb.append("\n# Knockout Stage Configuration\n");
            sb.append("game.stage=").append(request.getStageName()).append("\n");
            sb.append("game.leg=").append(request.getStageLeg()).append("\n");
            sb.append("game.swappedStartingPlayer=").append(request.isStageSwappedStartingPlayer()).append("\n");
            if (request.getStageLeg() == 2) {
                sb.append("game.firstLegResult=").append(request.getStageFirstLegResultP0()).append(":").append(request.getStageFirstLegResultP1()).append("\n");
                sb.append("game.firstLegCardsBought=").append(request.getStageFirstLegCardsP0()).append(":").append(request.getStageFirstLegCardsP1()).append("\n");
            }
        }

        byte[] propertiesBytes = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"game.properties\"")
                .body(propertiesBytes);
    }

    @PostMapping("/matches/{gameId}/abort")
    public ResponseEntity<?> abortMatch(@PathVariable String gameId) {
        try {
            logger.info("REST request to abort match: {}", gameId);
            boolean success = matchManagerService.abortMatch(gameId);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "Match successfully aborted"));
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "Match not running or not found"));
            }
        } catch (Exception e) {
            logger.error("Failed to abort match " + gameId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }
}
