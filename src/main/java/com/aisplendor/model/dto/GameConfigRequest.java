package com.aisplendor.model.dto;

public class GameConfigRequest {
    private String player0Model;
    private String player1Model;
    
    private boolean player0ReasoningEnabled;
    private String player0ReasoningEffort = "medium";
    private boolean player0ReasoningExclude = true;
    private boolean player0ReasoningDynamic;
    private String player0ReasoningPhases;
    private int player0MemorySize = 3;

    private boolean player1ReasoningEnabled;
    private String player1ReasoningEffort = "medium";
    private boolean player1ReasoningExclude = true;
    private boolean player1ReasoningDynamic;
    private String player1ReasoningPhases;
    private int player1MemorySize = 3;

    private String player0Name;
    private String player1Name;

    private boolean stageEnabled;
    private String stageName;
    private int stageLeg = 1;
    private boolean stageSwappedStartingPlayer;
    private int stageFirstLegResultP0;
    private int stageFirstLegResultP1;
    private int stageFirstLegCardsP0;
    private int stageFirstLegCardsP1;

    private boolean debugMode;
    private String promptCachingSetting = "auto";
    private String apiKeyOverride;

    private double player0InputTokenCost;
    private double player0OutputTokenCost;
    private double player1InputTokenCost;
    private double player1OutputTokenCost;

    // Getters and Setters
    public String getPlayer0Model() { return player0Model; }
    public void setPlayer0Model(String player0Model) { this.player0Model = player0Model; }

    public String getPlayer1Model() { return player1Model; }
    public void setPlayer1Model(String player1Model) { this.player1Model = player1Model; }

    public boolean isPlayer0ReasoningEnabled() { return player0ReasoningEnabled; }
    public void setPlayer0ReasoningEnabled(boolean player0ReasoningEnabled) { this.player0ReasoningEnabled = player0ReasoningEnabled; }

    public String getPlayer0ReasoningEffort() { return player0ReasoningEffort; }
    public void setPlayer0ReasoningEffort(String player0ReasoningEffort) { this.player0ReasoningEffort = player0ReasoningEffort; }

    public boolean isPlayer0ReasoningExclude() { return player0ReasoningExclude; }
    public void setPlayer0ReasoningExclude(boolean player0ReasoningExclude) { this.player0ReasoningExclude = player0ReasoningExclude; }

    public boolean isPlayer0ReasoningDynamic() { return player0ReasoningDynamic; }
    public void setPlayer0ReasoningDynamic(boolean player0ReasoningDynamic) { this.player0ReasoningDynamic = player0ReasoningDynamic; }

    public String getPlayer0ReasoningPhases() { return player0ReasoningPhases; }
    public void setPlayer0ReasoningPhases(String player0ReasoningPhases) { this.player0ReasoningPhases = player0ReasoningPhases; }

    public int getPlayer0MemorySize() { return player0MemorySize; }
    public void setPlayer0MemorySize(int player0MemorySize) { this.player0MemorySize = player0MemorySize; }

    public boolean isPlayer1ReasoningEnabled() { return player1ReasoningEnabled; }
    public void setPlayer1ReasoningEnabled(boolean player1ReasoningEnabled) { this.player1ReasoningEnabled = player1ReasoningEnabled; }

    public String getPlayer1ReasoningEffort() { return player1ReasoningEffort; }
    public void setPlayer1ReasoningEffort(String player1ReasoningEffort) { this.player1ReasoningEffort = player1ReasoningEffort; }

    public boolean isPlayer1ReasoningExclude() { return player1ReasoningExclude; }
    public void setPlayer1ReasoningExclude(boolean player1ReasoningExclude) { this.player1ReasoningExclude = player1ReasoningExclude; }

    public boolean isPlayer1ReasoningDynamic() { return player1ReasoningDynamic; }
    public void setPlayer1ReasoningDynamic(boolean player1ReasoningDynamic) { this.player1ReasoningDynamic = player1ReasoningDynamic; }

    public String getPlayer1ReasoningPhases() { return player1ReasoningPhases; }
    public void setPlayer1ReasoningPhases(String player1ReasoningPhases) { this.player1ReasoningPhases = player1ReasoningPhases; }

    public int getPlayer1MemorySize() { return player1MemorySize; }
    public void setPlayer1MemorySize(int player1MemorySize) { this.player1MemorySize = player1MemorySize; }

    public boolean isDebugMode() { return debugMode; }
    public void setDebugMode(boolean debugMode) { this.debugMode = debugMode; }

    public String getPromptCachingSetting() { return promptCachingSetting; }
    public void setPromptCachingSetting(String promptCachingSetting) { this.promptCachingSetting = promptCachingSetting; }

    public String getApiKeyOverride() { return apiKeyOverride; }
    public void setApiKeyOverride(String apiKeyOverride) { this.apiKeyOverride = apiKeyOverride; }

    public String getPlayer0Name() { return player0Name; }
    public void setPlayer0Name(String player0Name) { this.player0Name = player0Name; }

    public String getPlayer1Name() { return player1Name; }
    public void setPlayer1Name(String player1Name) { this.player1Name = player1Name; }

    public boolean isStageEnabled() { return stageEnabled; }
    public void setStageEnabled(boolean stageEnabled) { this.stageEnabled = stageEnabled; }

    public String getStageName() { return stageName; }
    public void setStageName(String stageName) { this.stageName = stageName; }

    public int getStageLeg() { return stageLeg; }
    public void setStageLeg(int stageLeg) { this.stageLeg = stageLeg; }

    public boolean isStageSwappedStartingPlayer() { return stageSwappedStartingPlayer; }
    public void setStageSwappedStartingPlayer(boolean stageSwappedStartingPlayer) { this.stageSwappedStartingPlayer = stageSwappedStartingPlayer; }

    public int getStageFirstLegResultP0() { return stageFirstLegResultP0; }
    public void setStageFirstLegResultP0(int stageFirstLegResultP0) { this.stageFirstLegResultP0 = stageFirstLegResultP0; }

    public int getStageFirstLegResultP1() { return stageFirstLegResultP1; }
    public void setStageFirstLegResultP1(int stageFirstLegResultP1) { this.stageFirstLegResultP1 = stageFirstLegResultP1; }

    public int getStageFirstLegCardsP0() { return stageFirstLegCardsP0; }
    public void setStageFirstLegCardsP0(int stageFirstLegCardsP0) { this.stageFirstLegCardsP0 = stageFirstLegCardsP0; }

    public int getStageFirstLegCardsP1() { return stageFirstLegCardsP1; }
    public void setStageFirstLegCardsP1(int stageFirstLegCardsP1) { this.stageFirstLegCardsP1 = stageFirstLegCardsP1; }

    public double getPlayer0InputTokenCost() { return player0InputTokenCost; }
    public void setPlayer0InputTokenCost(double cost) { this.player0InputTokenCost = cost; }

    public double getPlayer0OutputTokenCost() { return player0OutputTokenCost; }
    public void setPlayer0OutputTokenCost(double cost) { this.player0OutputTokenCost = cost; }

    public double getPlayer1InputTokenCost() { return player1InputTokenCost; }
    public void setPlayer1InputTokenCost(double cost) { this.player1InputTokenCost = cost; }

    public double getPlayer1OutputTokenCost() { return player1OutputTokenCost; }
    public void setPlayer1OutputTokenCost(double cost) { this.player1OutputTokenCost = cost; }
}
