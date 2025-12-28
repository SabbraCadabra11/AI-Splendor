package com.aisplendor.engine;

import com.aisplendor.model.*;
import com.aisplendor.model.action.*;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Core game logic engine.
 * Responsible for validating actions, applying actions to the state, and
 * checking game rules.
 */
public class GameEngine {

    /**
     * Validates if the proposed action is legal given the current game state.
     *
     * @param state  The current game state.
     * @param action The action to validate.
     * @throws IllegalArgumentException if the action is invalid, with a message
     *                                  explaining why.
     */
    public void validateAction(GameState state, GameAction action) {
        if (state.isGameOver()) {
            throw new IllegalStateException("Game is already over.");
        }

        if (action instanceof TakeTokensAction takeTokens) {
            validateTakeTokens(state, takeTokens);
        } else if (action instanceof ReserveCardAction reserveCard) {
            validateReserveCard(state, reserveCard);
        } else if (action instanceof PurchaseCardAction purchaseCard) {
            validatePurchaseCard(state, purchaseCard);
        } else {
            throw new IllegalArgumentException("Unknown action type.");
        }
    }

    private void validateTakeTokens(GameState state, TakeTokensAction action) {
        Player player = state.players().get(state.currentPlayerIndex());
        Map<Color, Integer> tokens = action.tokens();
        if (tokens == null || tokens.isEmpty()) {
            throw new IllegalArgumentException("Must select tokens to take.");
        }

        // Cannot take GOLD directly
        if (tokens.containsKey(Color.GOLD)) {
            throw new IllegalArgumentException("Cannot take GOLD tokens directly.");
        }

        int totalTaking = tokens.values().stream().mapToInt(Integer::intValue).sum();
        long distinctColors = tokens.keySet().stream().filter(c -> tokens.get(c) > 0).count();

        Board board = state.board();

        if (totalTaking == 3) {
            // TAKE_3_DIFFERENT
            if (distinctColors != 3) {
                throw new IllegalArgumentException("For taking 3 tokens, they must be of 3 different colors.");
            }
            // Check availability
            for (Color c : tokens.keySet()) {
                if (board.availableTokens().getCount(c) < 1) {
                    throw new IllegalArgumentException("Not enough " + c + " tokens available.");
                }
            }
        } else if (totalTaking == 2) {
            // TAKE_2_SAME
            if (distinctColors != 1) {
                throw new IllegalArgumentException("For taking 2 tokens, they must be of the same color.");
            }
            Color color = tokens.keySet().iterator().next();
            if (board.availableTokens().getCount(color) < 4) {
                throw new IllegalArgumentException("Cannot take 2 " + color + " tokens: only "
                        + board.availableTokens().getCount(color) + " available (need 4).");
            }
        } else {
            throw new IllegalArgumentException("Invalid token count. Must take 3 different or 2 matching tokens.");
        }

        // Validate discard limits (must not end with > 10 tokens)
        int currentTotal = player.tokens().totalTokens();
        int takingTotal = tokens.values().stream().mapToInt(Integer::intValue).sum();
        int returningTotal = action.tokensToReturn() != null
                ? action.tokensToReturn().values().stream().mapToInt(Integer::intValue).sum()
                : 0;

        if (currentTotal + takingTotal - returningTotal > 10) {
            throw new IllegalArgumentException(
                    "Cannot have more than 10 tokens at end of turn. Current: " + currentTotal
                            + ", Taking: " + takingTotal + ", Returning: " + returningTotal);
        }

        // Ensure returning tokens are actually possessed
        if (action.tokensToReturn() != null) {
            for (Map.Entry<Color, Integer> entry : action.tokensToReturn().entrySet()) {
                int current = player.tokens().getCount(entry.getKey());
                int newlyTaken = tokens.getOrDefault(entry.getKey(), 0);
                if (current + newlyTaken < entry.getValue()) {
                    throw new IllegalArgumentException("Cannot return " + entry.getValue() + " " + entry.getKey()
                            + " tokens: player only has " + (current + newlyTaken) + " available (including taken).");
                }
            }
        }
    }

    private void validateReserveCard(GameState state, ReserveCardAction action) {
        Player player = state.players().get(state.currentPlayerIndex());

        // 1. Check reservation limit
        if (player.reservedCards().size() >= 3) {
            throw new IllegalArgumentException("Cannot reserve more than 3 cards.");
        }

        Board board = state.board();

        // 2. Identify target
        if (action.cardId() != null) {
            // Reserving from board
            boolean found = false;
            for (List<DevelopmentCard> levelCards : board.faceUpCards().values()) {
                if (levelCards.stream().anyMatch(c -> c.id().equals(action.cardId()))) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new IllegalArgumentException("Card with ID " + action.cardId() + " not found on board.");
            }
        } else if (action.deckLevel() != null) {
            // Reserving from deck
            var deck = board.decks().get(action.deckLevel());
            if (deck == null || deck.isEmpty()) {
                throw new IllegalArgumentException("Deck " + action.deckLevel() + " is empty.");
            }
        } else {
            throw new IllegalArgumentException("Must specify either cardId or deckLevel to reserve.");
        }

        // Validation for token limit (max 10) - Reserving gives 1 gold if available
        int goldGained = board.availableTokens().getCount(Color.GOLD) > 0 ? 1 : 0;
        int currentTotal = player.tokens().totalTokens();
        int returningTotal = action.tokensToReturn() != null
                ? action.tokensToReturn().values().stream().mapToInt(Integer::intValue).sum()
                : 0;

        if (currentTotal + goldGained - returningTotal > 10) {
            throw new IllegalArgumentException(
                    "Cannot have more than 10 tokens at end of turn. Current: " + currentTotal
                            + ", Gaining Gold: " + goldGained + ", Returning: " + returningTotal);
        }

        // Ensure returning tokens are actually possessed
        if (action.tokensToReturn() != null) {
            for (Map.Entry<Color, Integer> entry : action.tokensToReturn().entrySet()) {
                int current = player.tokens().getCount(entry.getKey());
                int newlyTaken = (entry.getKey() == Color.GOLD) ? goldGained : 0;
                if (current + newlyTaken < entry.getValue()) {
                    throw new IllegalArgumentException("Cannot return " + entry.getValue() + " " + entry.getKey()
                            + " tokens: player only has " + (current + newlyTaken) + " available.");
                }
            }
        }
    }

    private void validatePurchaseCard(GameState state, PurchaseCardAction action) {
        Player player = state.players().get(state.currentPlayerIndex());
        DevelopmentCard card = findCard(state, action.cardId(), player);

        if (card == null) {
            throw new IllegalArgumentException("Card " + action.cardId() + " not found on board or in reserved hand.");
        }

        // Calculate gold needed
        int goldNeeded = 0;
        for (Map.Entry<Color, Integer> entry : card.cost().entrySet()) {
            Color color = entry.getKey();
            int cost = entry.getValue();
            int bonus = player.bonuses().getOrDefault(color, 0);
            int tokens = player.tokens().getCount(color);
            int deficit = Math.max(0, cost - bonus - tokens);
            goldNeeded += deficit;
        }

        if (player.tokens().getCount(Color.GOLD) < goldNeeded) {
            throw new IllegalArgumentException("Insufficient tokens to purchase card " + action.cardId());
        }
    }

    private DevelopmentCard findCard(GameState state, String cardId, Player player) {
        // Check board
        for (List<DevelopmentCard> list : state.board().faceUpCards().values()) {
            for (DevelopmentCard c : list) {
                if (c.id().equals(cardId))
                    return c;
            }
        }
        // Check player reserved
        for (DevelopmentCard c : player.reservedCards()) {
            if (c.id().equals(cardId))
                return c;
        }
        return null;
    }

    /**
     * Applies the action to the current state and returns the new state.
     * Use validateAction before calling this.
     *
     * @param state  The current game state.
     * @param action The action to apply.
     * @return The new game state.
     */
    public GameState applyAction(GameState state, GameAction action) {
        if (action instanceof TakeTokensAction takeTokens) {
            return applyTakeTokens(state, takeTokens);
        } else if (action instanceof ReserveCardAction reserveCard) {
            return applyReserveCard(state, reserveCard);
        } else if (action instanceof PurchaseCardAction purchaseCard) {
            return applyPurchaseCard(state, purchaseCard);
        } else {
            throw new IllegalArgumentException("Unknown action type.");
        }
    }

    private GameState applyTakeTokens(GameState state, TakeTokensAction action) {
        Board board = state.board();
        Player player = state.players().get(state.currentPlayerIndex());

        Map<Color, Integer> takenTokens = action.tokens();

        // Update Board Bank
        Map<Color, Integer> newBoardTokens = new EnumMap<>(Color.class);
        newBoardTokens.putAll(board.availableTokens().counts());
        takenTokens.forEach((color, count) -> {
            newBoardTokens.put(color, newBoardTokens.get(color) - count);
        });

        // Update Player Bank
        Map<Color, Integer> newPlayerTokens = new EnumMap<>(Color.class);
        newPlayerTokens.putAll(player.tokens().counts());
        takenTokens.forEach((color, count) -> {
            newPlayerTokens.put(color, newPlayerTokens.getOrDefault(color, 0) + count);
        });

        // Handle Discards
        if (action.tokensToReturn() != null) {
            action.tokensToReturn().forEach((color, count) -> {
                if (count != null && count > 0) {
                    newPlayerTokens.put(color, newPlayerTokens.getOrDefault(color, 0) - count);
                    newBoardTokens.put(color, newBoardTokens.getOrDefault(color, 0) + count);
                }
            });
        }

        Player newPlayer = new Player(
                player.id(),
                new TokenBank(newPlayerTokens),
                player.purchasedCards(),
                player.reservedCards(),
                player.visitedNobles(),
                player.score(),
                player.bonuses(),
                player.reasoningHistory());

        Board newBoard = new Board(
                new TokenBank(newBoardTokens),
                board.faceUpCards(),
                board.decks(),
                board.availableNobles());

        List<Player> newPlayers = new ArrayList<>(state.players());
        newPlayers.set(state.currentPlayerIndex(), newPlayer);

        return finalizeTurn(new GameState(
                newBoard,
                newPlayers,
                state.currentPlayerIndex(),
                state.turnNumber(),
                state.isGameOver(),
                state.winnerReason()));
    }

    private GameState applyReserveCard(GameState state, ReserveCardAction action) {
        Board board = state.board();
        Player player = state.players().get(state.currentPlayerIndex());

        // 1. Get the card and update board
        DevelopmentCard reservedCard = null;
        Map<CardLevel, List<DevelopmentCard>> newFaceUpCards = new EnumMap<>(CardLevel.class);
        newFaceUpCards.putAll(board.faceUpCards());
        Map<CardLevel, Queue<DevelopmentCard>> newDecks = new EnumMap<>(CardLevel.class);
        newDecks.putAll(board.decks());

        // Deep copy queues because we might modify them
        for (Map.Entry<CardLevel, Queue<DevelopmentCard>> entry : board.decks().entrySet()) {
            newDecks.put(entry.getKey(), new LinkedList<>(entry.getValue()));
        }

        // Deep copy lists in faceUpCards because we modify them
        for (Map.Entry<CardLevel, List<DevelopmentCard>> entry : board.faceUpCards().entrySet()) {
            newFaceUpCards.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        if (action.cardId() != null) {
            // Find and remove from board
            for (Map.Entry<CardLevel, List<DevelopmentCard>> entry : newFaceUpCards.entrySet()) {
                var list = entry.getValue();
                var iterator = list.iterator();
                while (iterator.hasNext()) {
                    DevelopmentCard c = iterator.next();
                    if (c.id().equals(action.cardId())) {
                        reservedCard = c;
                        iterator.remove();
                        // Refill immediately if deck has cards
                        var deck = newDecks.get(c.level());
                        if (deck != null && !deck.isEmpty()) {
                            list.add(deck.poll());
                        }
                        break;
                    }
                }
                if (reservedCard != null)
                    break;
            }
        } else {
            // Draw from deck
            var deck = newDecks.get(action.deckLevel());
            reservedCard = deck.poll();
        }

        // 2. Add to player reserved
        List<DevelopmentCard> newReserved = new ArrayList<>(player.reservedCards());
        newReserved.add(reservedCard);

        // 3. Handle Gold
        Map<Color, Integer> newBoardTokens = new EnumMap<>(Color.class);
        newBoardTokens.putAll(board.availableTokens().counts());

        Map<Color, Integer> newPlayerTokens = new EnumMap<>(Color.class);
        newPlayerTokens.putAll(player.tokens().counts());

        if (newBoardTokens.getOrDefault(Color.GOLD, 0) > 0) {
            newBoardTokens.put(Color.GOLD, newBoardTokens.get(Color.GOLD) - 1);
            newPlayerTokens.put(Color.GOLD, newPlayerTokens.getOrDefault(Color.GOLD, 0) + 1);
        }

        // 4. Handle Discards (if limit exceeded logic - similar to TakeTokens)
        if (action.tokensToReturn() != null) {
            action.tokensToReturn().forEach((color, count) -> {
                if (count != null && count > 0) {
                    newPlayerTokens.put(color, newPlayerTokens.getOrDefault(color, 0) - count);
                    newBoardTokens.put(color, newBoardTokens.getOrDefault(color, 0) + count);
                }
            });
        }

        // Construct new state
        Player newPlayer = new Player(
                player.id(),
                new TokenBank(newPlayerTokens),
                player.purchasedCards(),
                newReserved,
                player.visitedNobles(),
                player.score(),
                player.bonuses(),
                player.reasoningHistory());

        Board newBoard = new Board(
                new TokenBank(newBoardTokens),
                newFaceUpCards,
                newDecks,
                board.availableNobles());

        List<Player> newPlayers = new ArrayList<>(state.players());
        newPlayers.set(state.currentPlayerIndex(), newPlayer);

        return finalizeTurn(new GameState(
                newBoard,
                newPlayers,
                state.currentPlayerIndex(),
                state.turnNumber(),
                state.isGameOver(),
                state.winnerReason()));
    }

    private GameState applyPurchaseCard(GameState state, PurchaseCardAction action) {
        Board board = state.board();
        Player player = state.players().get(state.currentPlayerIndex());
        DevelopmentCard card = findCard(state, action.cardId(), player);

        // 1. Prepare new Board/Player components
        Map<CardLevel, List<DevelopmentCard>> newFaceUpCards = new EnumMap<>(CardLevel.class);
        newFaceUpCards.putAll(board.faceUpCards());
        Map<CardLevel, Queue<DevelopmentCard>> newDecks = new EnumMap<>(CardLevel.class);
        newDecks.putAll(board.decks());

        // Deep copy lists and queues
        for (Map.Entry<CardLevel, Queue<DevelopmentCard>> entry : board.decks().entrySet()) {
            newDecks.put(entry.getKey(), new LinkedList<>(entry.getValue()));
        }
        for (Map.Entry<CardLevel, List<DevelopmentCard>> entry : board.faceUpCards().entrySet()) {
            newFaceUpCards.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        Map<Color, Integer> newBoardTokens = new EnumMap<>(Color.class);
        newBoardTokens.putAll(board.availableTokens().counts());
        Map<Color, Integer> newPlayerTokens = new EnumMap<>(Color.class);
        newPlayerTokens.putAll(player.tokens().counts());

        // 2. Transact payment
        int goldNeeded = 0;
        for (Map.Entry<Color, Integer> entry : card.cost().entrySet()) {
            Color color = entry.getKey();
            int cost = entry.getValue();
            int bonus = player.bonuses().getOrDefault(color, 0);
            int payableWithTokens = Math.max(0, cost - bonus);
            int tokensToUse = Math.min(payableWithTokens, player.tokens().getCount(color));
            int deficit = payableWithTokens - tokensToUse;

            newPlayerTokens.put(color, newPlayerTokens.getOrDefault(color, 0) - tokensToUse);
            newBoardTokens.put(color, newBoardTokens.getOrDefault(color, 0) + tokensToUse);
            goldNeeded += deficit;
        }

        if (goldNeeded > 0) {
            newPlayerTokens.put(Color.GOLD, newPlayerTokens.get(Color.GOLD) - goldNeeded);
            newBoardTokens.put(Color.GOLD, newBoardTokens.getOrDefault(Color.GOLD, 0) + goldNeeded);
        }

        // 3. Move card to tableau
        List<DevelopmentCard> newReserved = new ArrayList<>(player.reservedCards());
        boolean removedFromReserved = newReserved.removeIf(c -> c.id().equals(card.id()));

        if (!removedFromReserved) {
            // Remove from board and refill
            List<DevelopmentCard> row = newFaceUpCards.get(card.level());
            row.removeIf(c -> c.id().equals(card.id()));
            Queue<DevelopmentCard> deck = newDecks.get(card.level());
            if (deck != null && !deck.isEmpty()) {
                row.add(deck.poll());
            }
        }

        List<DevelopmentCard> newPurchased = new ArrayList<>(player.purchasedCards());
        newPurchased.add(card);

        // Update Bonuses and Score
        Map<Color, Integer> newBonuses = new EnumMap<>(Color.class);
        newBonuses.putAll(player.bonuses());
        newBonuses.put(card.bonusGem(), newBonuses.getOrDefault(card.bonusGem(), 0) + 1);
        int newScore = player.score() + card.prestigePoints();

        Player newPlayer = new Player(
                player.id(),
                new TokenBank(newPlayerTokens),
                newPurchased,
                newReserved,
                player.visitedNobles(),
                newScore,
                newBonuses,
                player.reasoningHistory());

        Board newBoard = new Board(
                new TokenBank(newBoardTokens),
                newFaceUpCards,
                newDecks,
                board.availableNobles());

        // Finalize state (turn switching happens in applyAction or here?)
        // Spec says round finishes when someone hits 15.
        // I'll handle turn switching and automatic rules in a wrapper.

        List<Player> newPlayers = new ArrayList<>(state.players());
        newPlayers.set(state.currentPlayerIndex(), newPlayer);

        // Wrap up turn logic (Noble checks, Next player, Win condition)
        return finalizeTurn(new GameState(
                newBoard,
                newPlayers,
                state.currentPlayerIndex(),
                state.turnNumber(),
                state.isGameOver(),
                state.winnerReason()));
    }

    private GameState finalizeTurn(GameState state) {
        Player player = state.players().get(state.currentPlayerIndex());

        // 1. Noble Visit
        List<NobleTile> reachableNobles = new ArrayList<>();
        for (NobleTile noble : state.board().availableNobles()) {
            boolean eligible = true;
            for (Map.Entry<Color, Integer> req : noble.requirement().entrySet()) {
                if (player.bonuses().getOrDefault(req.getKey(), 0) < req.getValue()) {
                    eligible = false;
                    break;
                }
            }
            if (eligible)
                reachableNobles.add(noble);
        }

        Player updatedPlayer = player;
        Board updatedBoard = state.board();
        if (!reachableNobles.isEmpty()) {
            // Simplification for v1: take the first one
            NobleTile visiting = reachableNobles.get(0);
            List<NobleTile> newVisited = new ArrayList<>(player.visitedNobles());
            newVisited.add(visiting);
            updatedPlayer = new Player(
                    player.id(), player.tokens(), player.purchasedCards(), player.reservedCards(),
                    newVisited, player.score() + visiting.prestigePoints(), player.bonuses(),
                    player.reasoningHistory());

            List<NobleTile> newAvailableNobles = new ArrayList<>(state.board().availableNobles());
            newAvailableNobles.remove(visiting);
            updatedBoard = new Board(
                    state.board().availableTokens(),
                    state.board().faceUpCards(),
                    state.board().decks(),
                    newAvailableNobles);
        }

        List<Player> updatedPlayers = new ArrayList<>(state.players());
        updatedPlayers.set(state.currentPlayerIndex(), updatedPlayer);

        // 2. Check End Game Condition
        boolean endTriggered = state.isGameOver();
        if (!endTriggered) {
            for (Player p : updatedPlayers) {
                if (p.score() >= 15) {
                    endTriggered = true;
                    break;
                }
            }
        }

        int nextPlayerIndex = (state.currentPlayerIndex() + 1) % state.players().size();
        int nextTurn = (nextPlayerIndex == 0) ? state.turnNumber() + 1 : state.turnNumber();

        boolean isFinalGameOver = endTriggered && nextPlayerIndex == 0;
        String winnerReason = null;
        if (isFinalGameOver) {
            // Determine winner
            Player p0 = updatedPlayers.get(0);
            Player p1 = updatedPlayers.get(1);
            if (p0.score() > p1.score())
                winnerReason = "Player 0 won on points.";
            else if (p1.score() > p0.score())
                winnerReason = "Player 1 won on points.";
            else {
                // Tie breaker: fewest cards
                if (p0.purchasedCards().size() < p1.purchasedCards().size())
                    winnerReason = "Player 0 won on tie-breaker (fewer cards).";
                else if (p1.purchasedCards().size() < p0.purchasedCards().size())
                    winnerReason = "Player 1 won on tie-breaker (fewer cards).";
                else
                    winnerReason = "It's a draw!";
            }
        }

        return new GameState(
                updatedBoard,
                updatedPlayers,
                nextPlayerIndex,
                nextTurn,
                isFinalGameOver,
                winnerReason);
    }
}
