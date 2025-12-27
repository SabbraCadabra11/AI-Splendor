# **Technical Specification: Java Splendor Game Engine (LLM vs LLM)**

## **1\. Executive Summary**

This document specifies the architecture, data models, and logic for a Java-based game engine implementing the board game **Splendor**. The system is designed to orchestrate a game between two AI agents powered by Large Language Models (LLMs) via the OpenRouter API. The engine handles strict rule enforcement, state management, and API communication, ensuring a valid game flow according to the official rules (specifically the 2-player variant).

## **2\. System Architecture**

### **2.1 Core Components**

1. **Game Engine**: The central controller that manages the game loop, validates moves, updates state, and enforces rules.  
2. **State Manager**: Maintains the integrity of the GameState object.  
3. **LLM Client**: An interface for communicating with OpenRouter API (HTTP/JSON).  
4. **Prompt Builder**: Converts the Java GameState into a text/JSON prompt optimized for LLM reasoning.  
5. **Response Parser**: Deserializes LLM JSON outputs into executable Java Action objects.

### **2.2 Technology Stack**

* **Language**: Java 17+ (Records, Sealed Classes recommended).  
* **JSON Processing**: Jackson or Gson.  
* **HTTP Client**: java.net.http.HttpClient.

## **3\. Data Models (Domain Objects)**

### **3.1 Enums**

* **Color**: WHITE (Diamond), BLUE (Sapphire), GREEN (Emerald), RED (Ruby), BLACK (Onyx), GOLD (Joker).  
* **CardLevel**: LEVEL\_1 (O), LEVEL\_2 (OO), LEVEL\_3 (OOO).

### **3.2 Records / Classes**

#### **TokenBank**

Represents a collection of gem tokens.

* **Fields**: Map\<Color, Integer\> counts  
* **Constraints**: Non-negative integers.

#### **DevelopmentCard**

* **Fields**:  
  * String id (Unique identifier)  
  * CardLevel level  
  * Color bonusGem (The gem provided by the card)  
  * int prestigePoints  
  * Map\<Color, Integer\> cost (Cost to purchase)

#### **NobleTile**

* **Fields**:  
  * String id  
  * int prestigePoints (Always 3\)  
  * Map\<Color, Integer\> requirement (Bonus gems required to visit)

#### **Player**

* **Fields**:  
  * int id (0 or 1\)  
  * TokenBank tokens (Current tokens in hand)  
  * List\<DevelopmentCard\> purchasedCards (Tableau)  
  * List\<DevelopmentCard\> reservedCards (Hand, max 3\)  
  * List\<NobleTile\> visitedNobles  
  * int score (Derived from purchased cards \+ nobles)  
  * Map\<Color, Integer\> bonuses (Derived from purchased cards)

#### **Board**

* **Fields**:  
  * TokenBank availableTokens  
  * Map\<CardLevel, List\<DevelopmentCard\>\> faceUpCards (4 per level)  
  * Map\<CardLevel, Queue\<DevelopmentCard\>\> decks (Remaining cards)  
  * List\<NobleTile\> availableNobles

#### **GameState**

* **Fields**:  
  * Board board  
  * List\<Player\> players  
  * int currentPlayerIndex  
  * int turnNumber  
  * boolean isGameOver  
  * String winnerReason

## **4\. Game Logic & Rules Implementation**

### **4.1 Setup (2-Player Variant)**

According to the rulebook for 2 players:

* **Nobles**: Reveal **3** Noble tiles (Players \+ 1).  
* **Tokens**: Start with **4** tokens of each gem color (Green, Blue, Red, White, Black).  
* **Gold**: Start with **5** Gold tokens (Standard).  
* **Decks**: Shuffle and deal 4 face-up cards for each of the 3 levels.

### **4.2 Action Types**

The engine must support exactly 4 distinct actions. The LLM must choose one per turn.

#### **A. TAKE\_3\_DIFFERENT\_TOKENS**

* **Parameters**: Set\<Color\> colors (Size must be 3).  
* **Validation**:  
  * colors must not include GOLD.  
  * Each selected color must have \> 0 tokens available in the bank.  
* **Effect**: Transfer 1 token of each selected color from Bank to Player.

#### **B. TAKE\_2\_SAME\_TOKENS**

* **Parameters**: Color color.  
* **Validation**:  
  * color must not be GOLD.  
  * Bank must have **4 or more** tokens of that specific color available.  
* **Effect**: Transfer 2 tokens of color from Bank to Player.

#### **C. RESERVE\_CARD**

* **Parameters**: String cardId (from board) OR CardLevel deckToDrawFrom.  
* **Validation**:  
  * Player must have fewer than 3 reserved cards.  
* **Effect**:  
  * Move card from Board/Deck to Player's reserved list.  
  * If Gold \> 0 in Bank, transfer 1 Gold to Player.  
  * Refill Board slot from the corresponding deck.

#### **D. PURCHASE\_CARD**

* **Parameters**: String cardId (must be on Board or in Reserved Hand).  
* **Validation**:  
  * Player must have sufficient purchasing power (Bonuses \+ Tokens \+ Gold).  
  * **Cost Calculation**:  
    1. Effective Cost \= Card Cost \- Player Bonuses (min 0 for each color).  
    2. Remaining Cost must be covered by Gem Tokens.  
    3. Deficit can be covered by Gold Tokens (1 Gold \= 1 of any missing color).  
* **Effect**:  
  * Tokens used for payment are returned to the Bank.  
  * Card moves to Player's purchasedCards.  
  * If card was on Board, refill slot.

### **4.3 Automatic Triggered Rules**

#### **Token Limit Check**

* **Rule**: A player cannot have more than **10 tokens** at the end of their turn.  
* **Implementation**: If a player ends an action with \> 10 tokens, the engine must trigger a sub-request (or handle automatically based on heuristic, though for LLM agents, it is better to force the DISCARD\_TOKENS decision if the count exceeds 10, or include the discard choice in the initial Action payload).  
* *Specification Decision*: The Action output from the LLM should include an optional field tokensToDiscard (Map\<Color, Int\>) if they anticipate exceeding the limit. If they exceed and didn't specify, the engine fails the move or auto-discards (heuristic: keep gold, then gems needed for reserved cards). *Recommendation: Fail the move and ask for retry with discard details.*

#### **Noble Visit**

* **Rule**: At the end of turn, check if player bonuses meet Noble requirements.  
* **Logic**:  
  * Matches \= All available Nobles where Noble.requirements \<= Player.bonuses.  
  * If matches count \== 1: Auto-acquire Noble.  
  * If matches count \> 1: Player must choose one (LLM input required). *Simplification for v1: Auto-pick the one with highest ID or random, as points are identical (3).*  
  * Nobles are not an action; they happen automatically.

### **4.4 End Game Condition**

* **Trigger**: A player reaches **15 or more prestige points**.  
* **Resolution**: Complete the current round so both players have equal turns.  
* **Winning**:  
  1. Highest Prestige Points.  
  2. Tie-breaker: Fewest Development Cards purchased.

## **5\. LLM API Interface**

### **5.1 OpenRouter Request Structure**

The system prompt should define the persona: "You are an expert Splendor AI player."

**Input Prompt (User Role)**:

{  
  "game\_state": {  
    "turn": 5,  
    "you": { "score": 4, "gems": {"RED":1, ...}, "bonuses": {...}, "reserved": \[...\] },  
    "opponent": { "score": 2, "gems": {...}, "bonuses": {...}, "reserved\_count": 1 },  
    "board": {  
      "nobles": \[...\],  
      "tokens\_available": {...},  
      "level\_3\_cards": \[ { "id": "L3\_1", "color": "BLUE", "points": 4, "cost": {...} }, ... \],  
      "level\_2\_cards": \[...\],  
      "level\_1\_cards": \[...\]  
    }  
  },  
  "valid\_actions\_hint": "You can take gems, buy card X, or reserve."  
}

### **5.2 Structured Output Specification (JSON Mode)**

The LLMs must return a valid JSON object.

{  
  "type": "object",  
  "properties": {  
    "reasoning": {  
      "type": "string",  
      "description": "Chain of thought explaining the strategy, analyzing board state, and why this move was chosen."  
    },  
    "action": {  
      "type": "object",  
      "properties": {  
        "type": {  
          "type": "string",  
          "enum": \["TAKE\_3\_DIFF", "TAKE\_2\_SAME", "RESERVE", "PURCHASE"\]  
        },  
        "tokens": {  
          "type": "object",  
          "description": "Used for TAKE actions. Map of Color to Count."  
        },  
        "cardId": {  
          "type": "string",  
          "description": "Used for RESERVE or PURCHASE."  
        },  
        "reserveDeckLevel": {  
            "type": "string",  
            "enum": \["LEVEL\_1", "LEVEL\_2", "LEVEL\_3"\],  
            "description": "Used for RESERVE blind from deck."  
        },  
        "tokensToReturn": {  
           "type": "object",  
           "description": "If total tokens \> 10 after this move, specify which to discard."  
        }  
      },  
      "required": \["type"\]  
    }  
  },  
  "required": \["reasoning", "action"\]  
}

## **6\. Game Loop Algorithm**

1. **Initialization**:  
   * Load Card CSV/JSON data.  
   * Shuffle Decks and Nobles.  
   * Set up Board (Tokens: 4 each, Gold: 5).  
2. **Turn Loop**:  
   * Identify CurrentPlayer.  
   * **Serialize State**: Convert Java object to JSON.  
   * **Call LLM**: Send state \+ schema to OpenRouter.  
   * **Receive Response**: Parse JSON.  
   * **Validate**:  
     * Is syntax valid?  
     * Is move legal per game rules?  
     * *If Invalid*: (Optional) Retry loop with error message, or forfeit turn/game.  
   * **Execute Action**:  
     * Update Player tokens/cards.  
     * Update Board tokens/cards.  
     * Handle "Return Tokens" if \> 10\.  
   * **Check Nobles**: Assign Noble if eligible.  
   * **Check Win Condition**:  
     * If Player.score \>= 15, set lastRound flag.  
   * **Switch Player**: If lastRound is active and Player 2 just finished, End Game.  
3. **End Game**:  
   * Calculate final scores.  
   * Apply tie-breakers.  
   * Log Game Summary.

## **7\. Error Handling & Edge Cases**

* **Token Shortage**: If a player tries to take tokens that aren't available. \-\> Validator must reject.  
* **Deck Empty**: If a deck runs out, no new card replaces the empty spot. The spot remains empty.  
* **LLM Hallucination**: If LLM invents a card ID or action. \-\> Validator checks against canonical Board state.  
* **Connection Failure**: OpenRouter API timeout. \-\> Retry logic (Exponential backoff).

## **8\. Java Interface Definition**

public interface SplendorAI {  
    /\*\*  
     \* @param state The current full view of the game.  
     \* @return The structured response containing reasoning and action.  
     \*/  
    AgentResponse decideMove(GameState state);  
}

public record AgentResponse(String reasoning, GameAction action) {}

public sealed interface GameAction permits TakeTokens, ReserveCard, PurchaseCard {}  
// Detailed records for TakeTokens, ReserveCard, etc.  
