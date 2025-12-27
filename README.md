# AI-Splendor

A Java-based game engine that pits Large Language Models against each other in the strategic board game **Splendor**. Watch as AI agents compete, make strategic decisions, and vie for victory using real-time reasoning.

## Overview

AI-Splendor implements the complete 2-player variant of the Splendor board game, enabling autonomous games between LLM-powered agents via the [OpenRouter API](https://openrouter.ai/). The engine handles:

- Full game rule enforcement and validation
- State management and turn orchestration
- LLM prompt construction and response parsing
- Configurable model selection and reasoning parameters

## Features

- **LLM vs LLM Gameplay**: Two AI agents play a complete game of Splendor autonomously
- **Multi-Model Support**: Configure different LLM models for each player (Gemini, Claude, Grok, etc.)
- **Reasoning Configuration**: Enable and tune reasoning effort for supported models
- **Strict Rule Enforcement**: All moves are validated against official Splendor rules
- **Structured Output**: Uses JSON schema to ensure reliable action parsing from LLMs
- **Detailed Logging**: Game actions and LLM reasoning are logged for analysis

## Prerequisites

- Java 21+
- Maven 3.8+
- OpenRouter API key

## Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/AI-Splendor.git
   cd AI-Splendor
   ```

2. Set your OpenRouter API key as an environment variable:
   ```bash
   export OPENROUTER_API_KEY=your_api_key_here
   ```

3. Build the project:
   ```bash
   mvn clean compile
   ```

## Configuration

Edit `src/main/resources/application.properties` to configure the game:

```properties
# Player 0 configuration
player0.model=google/gemini-3-flash-preview
player0.reasoning.enabled=true
player0.reasoning.effort=medium

# Player 1 configuration
player1.model=x-ai/grok-4.1-fast
# player1.reasoning.enabled=false (default)

# Game settings
game.semi-auto=false
```

### Configuration Options

| Property | Description | Default |
|----------|-------------|---------|
| `playerN.model` | OpenRouter model identifier | `google/gemini-3-flash-preview` (P0), `anthropic/claude-haiku-4.5` (P1) |
| `playerN.reasoning.enabled` | Enable reasoning mode for the model | `false` |
| `playerN.reasoning.effort` | Reasoning effort level (`low`, `medium`, `high`) | `medium` |
| `game.semi-auto` | Pause between turns for manual inspection | `false` |

## Usage

Run the game simulator:

```bash
mvn exec:java
```

Or run directly with:

```bash
mvn exec:java -Dexec.mainClass="com.aisplendor.App"
```

## Project Structure

```
src/main/java/com/aisplendor/
├── App.java                    # Application entry point
├── config/
│   ├── GameConfig.java         # Configuration loader
│   └── ReasoningConfig.java    # LLM reasoning settings
├── engine/
│   ├── GameEngine.java         # Core game logic and validation
│   └── GameSimulator.java      # Game loop controller
├── model/
│   ├── Board.java              # Game board state
│   ├── Color.java              # Gem colors enum
│   ├── CardLevel.java          # Card tier levels
│   ├── DevelopmentCard.java    # Card data structure
│   ├── GameState.java          # Complete game state
│   ├── NobleTile.java          # Noble tile data
│   ├── Player.java             # Player state
│   ├── TokenBank.java          # Token collection
│   └── action/                 # Action types and responses
├── service/
│   ├── OpenRouterService.java  # LLM API client
│   └── PromptService.java      # Prompt generation
└── util/
    └── GameStateFormatter.java # State serialization
```

## Game Rules (2-Player Variant)

The engine implements official Splendor 2-player rules:

- **Tokens**: 4 of each gem color, 5 gold tokens
- **Nobles**: 3 noble tiles
- **Actions per turn**: Take gems, reserve a card, or purchase a card
- **Token limit**: Maximum 10 tokens per player
- **Victory**: First to 15+ prestige points (complete the round, highest score wins)

### Supported Actions

| Action | Description |
|--------|-------------|
| `TAKE_3_DIFF` | Take 3 different gem tokens |
| `TAKE_2_SAME` | Take 2 tokens of the same color (requires 4+ available) |
| `RESERVE` | Reserve a card and gain 1 gold token |
| `PURCHASE` | Buy a card using gems and bonuses |

## Testing

Run the test suite:

```bash
mvn test
```

Run a specific test:

```bash
mvn test -Dtest=GameEngineTest
```

## Logs

Game logs are written to the `logs/` directory, containing:

- Turn-by-turn game state
- LLM reasoning and decisions
- Move validation results
- Final game summary

## Documentation

See the `docs/` directory for additional documentation:

- `AI-Splendor_spec_EN.md` - Full technical specification

## License

This project is provided as-is for educational and research purposes.

## Acknowledgments

- [Splendor](https://www.spacecowboys.fr/splendor) board game by Space Cowboys
- [OpenRouter](https://openrouter.ai/) for LLM API access
