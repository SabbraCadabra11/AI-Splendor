# AI-Splendor

A Java-based game engine that pits Large Language Models against each other in the strategic board game **Splendor**. Watch as AI agents compete, make strategic decisions, and vie for victory using real-time reasoning through a web dashboard or command-line interface.

## Overview

AI-Splendor implements the complete 2-player variant of the Splendor board game, enabling autonomous games between LLM-powered agents via the [OpenRouter API](https://openrouter.ai/). 

The project has been migrated to **Spring Boot**, featuring:
- A rich **Multi-Match Configurator Web GUI** to set up, launch, and manage up to 8 parallel simulations
- A **High-Density Live Game Board Dashboard** connected via WebSockets to stream game events, scores, and real-time reasoning logs
- A **Replay Timeline Control Panel** to play, pause, step forward, and step backward through completed or active game steps
- A **Knockout Stage Context System** to inject tournament Leg 1 / Leg 2 rules, point differences, and tie-breakers into LLM prompt strategy

---

## Features

- **LLM vs LLM Autonomous Play**: Run autonomous matches between two models supported by OpenRouter (Gemini, Claude, DeepSeek, Llama, etc.).
- **Interactive Web UI**: Configurator dashboard to manage matches, review history, and view active board statuses visually.
- **WebSocket Event Streaming**: Live broadcast of board status, token reserves, player hand variables, and reasoning streams directly to the browser.
- **Replay / Timeline Mode**: Interactively review finished or currently running games step-by-step using timeline slider controls.
- **Dynamic Reasoning Effort**: Fine-tune LLM reasoning intensity statically or dynamically across different phases (e.g., lower reasoning effort in early game, higher effort in late game).
- **Reasoning Memory System**: Compresses LLM reasoning history into structured one-liners and feeds a sliding memory window back to the model, preventing repetitive strategies or execution loops.
- **Tournament / Knockout Stage Context**: Injects round context (Quarter-final, Semi-final, Final) and Leg 2 parameters (first leg score, point differentials, card counts) so AIs optimize their moves to advance (e.g., playing defensively to maintain a lead, or aggressively to overcome a deficit).
- **Detailed Token Audit & Cost Tracker**: Audits actual input/output token usage per turn and accumulates real-world costs ($) based on custom pricing profiles per model.
- **Robust Network Recovery**: Implements auto-retries with exponential backoff for network/API issues. Includes illegal move retry logic that feeds back rejected actions and validation errors to the LLMs.
- **Game Resumption**: Resume interrupted simulations from NDJSON logs via the CLI or Web UI.

---

## Prerequisites

- **Java 21+**
- **Maven 3.8+**
- **OpenRouter API Key** (Set as environment variable `OPENROUTER_API_KEY`)

---

## Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/AI-Splendor.git
   cd AI-Splendor
   ```

2. Set your OpenRouter API key:
   * **Linux/macOS:**
     ```bash
     export OPENROUTER_API_KEY=your_api_key_here
     ```
   * **Windows (PowerShell):**
     ```powershell
     $env:OPENROUTER_API_KEY="your_api_key_here"
     ```

3. Build the project:
   ```bash
   mvn clean compile
   ```

---

## Usage

### 1. Launching the Web Dashboard (Default)

Running the application without arguments starts the Spring Boot web server on port `8080`:

```bash
mvn exec:java
```

Once started:
1. Open your browser and navigate to **`http://localhost:8080/`**
2. You will be greeted by the **Multi-Match Configurator** dashboard.
3. Configure your match slots, set API keys, and click **Run All Simulations**.
4. Click **Open Board** or **Replay** to watch games in real-time or step through them.

### 2. CLI Run: Start a New Game with Properties File

To start a simulation directly in the command line using a local properties file:

```bash
mvn exec:java -Dexec.args="src/main/resources/application.properties"
```

### 3. CLI Resume: Resume an Interrupted Game

If a game crashes or times out, resume execution from its machine-readable NDJSON log file:

```bash
mvn exec:java -Dexec.args="--resume logs/20260610_120000.json"
```

The engine will:
- Parse the state from the last successful action event
- Carry over accumulated costs, times, and models
- Begin execution with a suffix `_resumed_HHMMSS` added to the log filename

---

## Configuration

### Local Application Properties (`src/main/resources/application.properties`)

You can modify default parameters for CLI executions:

```properties
# Player 0 Configuration
player0.model=google/gemini-3.5-flash
player0.name=Gemini-3.5
player0.memory.size=3
player0.reasoning.enabled=true
player0.reasoning.effort=high
player0.reasoning.dynamic=true
player0.reasoning.phases=1-5:medium,6+:high

# Player 1 Configuration
player1.model=anthropic/claude-opus-4.8
player1.name=Claude-Opus
player1.memory.size=3
player1.reasoning.enabled=true
player1.reasoning.effort=high
player1.reasoning.dynamic=true
player1.reasoning.phases=1-5:medium,6+:high

# Global Game Settings
game.semi-auto=false
game.debug-mode=false
game.prompt-caching=auto

# Knockout Tournament Stage Settings (Uncomment to enable)
# game.stage=final
# game.leg=2
# game.firstLegResult=12:15
# game.firstLegCardsBought=12:14
```

### Configuration Parameters

| Property | Description | Default / Example |
|----------|-------------|-------------------|
| `playerN.model` | OpenRouter model identifier | `google/gemini-3.5-flash` |
| `playerN.name` | Custom display name for player | `Gemini-3.5` |
| `playerN.memory.size` | Size of reasoning sliding window | `3` |
| `playerN.reasoning.enabled` | Enable reasoning model mode | `true` |
| `playerN.reasoning.effort` | Effort fallback level (`low`, `medium`, `high`) | `medium` |
| `playerN.reasoning.dynamic` | Adjust reasoning effort dynamically by turn | `true` |
| `playerN.reasoning.phases` | Range-effort mapping for dynamic modes | `1-5:medium,6+:high` |
| `game.semi-auto` | Pause CLI/sim between turns for stdin | `false` |
| `game.debug-mode` | Prints verbose JSON dumps of game states to log | `false` |
| `game.prompt-caching` | Control caching prompt blocks (`auto`, `true`, `false`) | `auto` |
| `game.stage` | Tournament stage context text | `final` / `semi-final` |
| `game.leg` | Match Leg number | `1` or `2` |
| `game.firstLegResult` | P0:P1 score result in first Leg (used only in Leg 2) | `12:15` |
| `game.firstLegCardsBought` | P0:P1 card purchase count in first Leg (used in Leg 2) | `12:14` |

---

## Project Structure

```
src/main/
├── java/com/aisplendor/
│   ├── App.java                   # Main application CLI/Web dispatcher entry point
│   ├── SpringApp.java             # Spring Boot server bootstrap class
│   ├── config/
│   │   ├── DynamicReasoningConfig.java # Dynamic reasoning configuration per phase
│   │   ├── GameConfig.java        # Properties parsing and loader utilities
│   │   ├── ReasoningConfig.java   # Static reasoning model configuration
│   │   ├── StageConfig.java       # Tournament stage and score metrics record
│   │   └── WebSocketConfig.java   # Spring WebSocket router mapping to /ws/game/*
│   ├── controller/
│   │   └── MatchController.java   # REST Endpoint handlers (Start, Resume, Logs, Exports)
│   ├── engine/
│   │   ├── GameEngine.java        # Core rules validation and state transitions
│   │   └── GameSimulator.java     # Game orchestration loop, retries, and timing audits
│   ├── model/
│   │   ├── Board.java             # Board state (bank, face up cards, noble list)
│   │   ├── CardLevel.java         # Tier indicators (1, 2, 3)
│   │   ├── Color.java             # Token colors (WHITE, BLUE, GREEN, RED, BLACK, GOLD)
│   │   ├── DeckFactory.java       # Shuffling and creation helper for cards/nobles
│   │   ├── DevelopmentCard.java   # Dev card properties
│   │   ├── GameState.java         # Whole game status snapshot
│   │   ├── NobleTile.java         # Nobles definition
│   │   ├── Player.java            # Player hands, tokens, bonuses, and memory window
│   │   ├── TokenBank.java         # Token helper structures
│   │   ├── TokenUsage.java        # Token counting and cost calculator record
│   │   ├── action/                # Executable move classes and parsers
│   │   ├── dto/                   # Frontend schema wrappers for configs and board state
│   │   └── event/                 # Game event definitions (Turn, Action, End, Reasoning)
│   ├── service/
│   │   ├── GameEventLogger.java   # Outputs NDJSON events to logs/ folder
│   │   ├── GameEventPublisher.java # Event multiplexer for WebSockets/Console
│   │   ├── GameLogReader.java     # Parsers to resume from NDJSON log files
│   │   ├── GameWebSocketHandler.java # WebSocket handler managing live dashboard streams
│   │   ├── MatchManagerService.java  # Runs matches on background threads and manages logs
│   │   ├── OpenRouterService.java # OpenRouter API handler with network retries
│   │   └── PromptService.java     # System prompts and retry generator
│   └── util/
│       ├── CompactStateSerializer.java # Compresses game state details
│       └── GameStateFormatter.java # Builds string representations for human logs
└── resources/
    ├── application.properties     # Global parameters config
    ├── development_cards.csv      # Standard database of Splendor development cards
    ├── logback.xml                # Logging formatter output configurations
    ├── prompts/
    │   ├── system_prompt.txt      # Master LLM system prompt instructions
    │   ├── stage_context_leg1.txt # Instructions injected for Leg 1 tournament aware play
    │   └── stage_context_leg2.txt # Injected targets for Leg 2 points/card tiebreaker strategies
    └── static/                    # Frontend GUI Dashboard Client
        ├── index.html             # Configurator panel
        ├── game_board.html        # Game dashboard & Replay client
        ├── css/style.css          # Design system stylesheet
        └── js/
            ├── configurator.js    # Client-side match controllers and websocket logs config
            └── game_board.js      # WebSocket listener and visual game renderer
```

---

## Game Rules (2-Player Variant)

The engine enforces standard 2-player rules:
- **Tokens**: 4 of each gem color (White, Blue, Green, Red, Black), 5 gold tokens (acting as wildcards).
- **Nobles**: 3 randomly selected noble tiles are revealed.
- **Actions per turn**: Take gems, reserve a card, or purchase a card.
- **Token limit**: A player cannot end their turn with more than 10 tokens. If they exceed this, they must choose tokens to discard down to 10.
- **Victory Conditions**: 15+ prestige points triggers the final round. Once players have had an equal number of turns, the player with the highest score wins.
- **Tie-breakers**: If scores are tied, the player who bought the **fewest development cards** wins.

### Leg 2 Tournament Strategy
In a Knockout stage (Leg 2), standard victory conditions are updated. The prompt injector guides the AI on its advancement strategy:
- **If leading after Leg 1**: Strategizes to maintain the lead (e.g. "Do not lose by more than X points").
- **If trailing after Leg 1**: Prioritizes catches (e.g. "Win this leg by at least Y points").
- **If tied**: Strategizes to win or buy fewer cards to win the tiebreaker.

---

## Logs

Game logs are generated in `logs/` in two formats:
- **Human-readable**: `YYYYMMDD_HHMMSS_AI-Splendor.log` - Visual representation of turns, scores, and full reasoning chains.
- **Machine-readable**: `YYYYMMDD_HHMMSS.json` - NDJSON format containing game events. This is parsed by the web client to feed the **Replay Timeline** and the backend to **Resume** games.

---

## Testing

Run the unit test suite:
```bash
mvn test
```

Run a specific test file:
```bash
mvn test -Dtest=GameEngineTest
```

---

## License

This project is provided as-is for educational and research purposes.

---

## Acknowledgments

- [Splendor](https://www.spacecowboys.fr/splendor) board game by Space Cowboys.
- [OpenRouter](https://openrouter.ai/) for LLM API access.

