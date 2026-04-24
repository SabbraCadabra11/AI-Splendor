package com.aisplendor.util;

import com.aisplendor.model.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Produces a compact, human-readable text representation of the game state
 * for LLM consumption. Designed to minimize token count while preserving
 * all information needed for strategic decision-making.
 *
 * Key design choices:
 * - 3-letter color codes (WHT, BLU, GRN, RED, BLK, GLD)
 * - Card IDs preserved exactly (LLM must reference them in actions)
 * - "You" / "Opponent" perspective based on current player
 * - Opponent's purchased card details omitted (only bonuses + score shown)
 * - Opponent's reserved card details hidden (only count shown)
 * - Zero-value tokens omitted
 * - No redundant 'level' field on cards (already grouped by level)
 */
public class CompactStateSerializer {

    /**
     * Serialize the game state into a compact text format for the LLM.
     *
     * @param state       The full game state
     * @param playerIndex The index of the player who will receive this state (their perspective)
     * @return Compact text representation
     */
    public static String serialize(GameState state, int playerIndex) {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("TURN %d | You are Player %d\n", state.turnNumber(), playerIndex));

        // Board tokens
        sb.append("\n[BOARD TOKENS] ");
        sb.append(formatTokenBank(state.board().availableTokens()));
        sb.append("\n");

        // Nobles
        sb.append("\n[NOBLES]\n");
        for (NobleTile noble : state.board().availableNobles()) {
            sb.append(String.format("%s (3pts) req: %s\n",
                    noble.id(), formatCost(noble.requirement())));
        }

        // Deck remaining counts (so the LLM knows blind reserve is possible)
        sb.append("\n[DECK SIZES] ");
        for (CardLevel level : CardLevel.values()) {
            var deck = state.board().decks().get(level);
            sb.append(String.format("%s:%d ", getLevelPrefix(level), deck != null ? deck.size() : 0));
        }
        sb.append("\n");

        // Face-up cards
        sb.append("\n[FACE-UP CARDS]\n");
        for (CardLevel level : CardLevel.values()) {
            String prefix = getLevelPrefix(level);
            List<DevelopmentCard> cards = state.board().faceUpCards().get(level);
            if (cards != null && !cards.isEmpty()) {
                sb.append(formatCardRow(prefix, cards));
            }
        }

        // Current player ("You")
        Player self = state.players().get(playerIndex);
        sb.append("\n[YOU] ");
        sb.append(formatSelf(self));

        // Opponent
        int opponentIndex = 1 - playerIndex;
        Player opponent = state.players().get(opponentIndex);
        sb.append("\n[OPPONENT] ");
        sb.append(formatOpponent(opponent));

        return sb.toString();
    }

    private static String formatSelf(Player player) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Score: %d | Cards: %d\n", player.score(), player.purchasedCards().size()));

        sb.append("  Tokens: ");
        String tokens = formatTokenBank(player.tokens());
        sb.append(tokens.isEmpty() ? "None" : tokens);
        sb.append("\n");

        sb.append("  Bonuses: ");
        sb.append(formatBonuses(player.bonuses()));
        sb.append("\n");

        // Show reserved cards with full details (player needs this to decide purchases)
        sb.append("  Reserved: ");
        if (player.reservedCards().isEmpty()) {
            sb.append("None\n");
        } else {
            for (int i = 0; i < player.reservedCards().size(); i++) {
                DevelopmentCard card = player.reservedCards().get(i);
                if (i > 0) {
                    sb.append("            ");
                }
                sb.append(formatCard(card));
                sb.append("\n");
            }
        }

        // Show visited nobles
        if (!player.visitedNobles().isEmpty()) {
            sb.append("  Nobles: ");
            sb.append(player.visitedNobles().stream()
                    .map(NobleTile::id)
                    .collect(Collectors.joining(", ")));
            sb.append("\n");
        }

        return sb.toString();
    }

    private static String formatOpponent(Player player) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Score: %d | Cards: %d\n", player.score(), player.purchasedCards().size()));

        sb.append("  Tokens: ");
        String tokens = formatTokenBank(player.tokens());
        sb.append(tokens.isEmpty() ? "None" : tokens);
        sb.append("\n");

        sb.append("  Bonuses: ");
        sb.append(formatBonuses(player.bonuses()));
        sb.append("\n");

        // Only show count for opponent's reserved cards (not details)
        int reservedCount = player.reservedCards().size();
        sb.append(String.format("  Reserved: %d card(s)\n", reservedCount));

        // Show visited nobles
        if (!player.visitedNobles().isEmpty()) {
            sb.append("  Nobles: ");
            sb.append(player.visitedNobles().stream()
                    .map(NobleTile::id)
                    .collect(Collectors.joining(", ")));
            sb.append("\n");
        }

        return sb.toString();
    }

    private static String formatCardRow(String levelPrefix, List<DevelopmentCard> cards) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cards.size(); i += 2) {
            if (i == 0) {
                sb.append(levelPrefix).append(": ");
            } else {
                sb.append("    ");
            }

            sb.append(formatCard(cards.get(i)));
            if (i + 1 < cards.size()) {
                sb.append(" | ").append(formatCard(cards.get(i + 1)));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static String formatCard(DevelopmentCard card) {
        String pts = card.prestigePoints() == 1 ? "1pt" : card.prestigePoints() + "pts";
        return String.format("[%s] %s %s %s",
                card.id(),
                formatColor(card.bonusGem()),
                pts,
                formatCost(card.cost()));
    }

    private static String formatTokenBank(TokenBank bank) {
        return bank.counts().entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(e -> String.format("%s:%d", formatColor(e.getKey()), e.getValue()))
                .collect(Collectors.joining(" "));
    }

    private static String formatBonuses(Map<Color, Integer> bonuses) {
        if (bonuses == null || bonuses.isEmpty()) {
            return "{}";
        }
        String inner = bonuses.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(e -> String.format("%s:%d", formatColor(e.getKey()), e.getValue()))
                .collect(Collectors.joining(" "));
        return inner.isEmpty() ? "{}" : "{" + inner + "}";
    }

    private static String formatCost(Map<Color, Integer> cost) {
        return cost.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(e -> String.format("%s:%d", formatColor(e.getKey()), e.getValue()))
                .collect(Collectors.joining(" "));
    }

    private static String formatColor(Color color) {
        return switch (color) {
            case WHITE -> "WHT";
            case BLUE -> "BLU";
            case GREEN -> "GRN";
            case RED -> "RED";
            case BLACK -> "BLK";
            case GOLD -> "GLD";
        };
    }

    private static String getLevelPrefix(CardLevel level) {
        return switch (level) {
            case LEVEL_1 -> "L1";
            case LEVEL_2 -> "L2";
            case LEVEL_3 -> "L3";
        };
    }
}
