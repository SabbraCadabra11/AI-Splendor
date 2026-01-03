package com.aisplendor.util;

import com.aisplendor.model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GameStateFormatter {

    private static final int CARDS_PER_ROW = 2;
    private static final int PLAYER_COLUMN_WIDTH = 55;
    private static final int CARD_WIDTH = 42; // Based on longest card: [L3_79] WHT 3pts BLU:3 GRN:3 RED:5 BLK:3

    public static String format(GameState state, List<String> modelNames) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n======================================================================================\n");
        String currentModel = extractModelName(modelNames.get(state.currentPlayerIndex()));
        sb.append(String.format("TURN %d | Current Player: %s (P%d)\n", state.turnNumber(), currentModel,
                state.currentPlayerIndex()));
        sb.append("======================================================================================\n");

        // Board Section
        sb.append("\n[BOARD BANK]\n");
        sb.append(formatTokenBankCompact(state.board().availableTokens()));
        sb.append("\n");

        sb.append("\n[FACE-UP CARDS]\n");
        for (CardLevel level : CardLevel.values()) {
            String levelPrefix = getLevelPrefix(level);
            List<DevelopmentCard> cards = state.board().faceUpCards().get(level);
            sb.append(formatCardsCompact(levelPrefix, cards));
        }

        // Players Section - side by side
        sb.append("\n[PLAYERS]\n");
        sb.append(formatPlayersSideBySide(state, modelNames));
        sb.append("\n======================================================================================\n");
        return sb.toString();
    }

    private static String getLevelPrefix(CardLevel level) {
        return switch (level) {
            case LEVEL_1 -> "L1";
            case LEVEL_2 -> "L2";
            case LEVEL_3 -> "L3";
        };
    }

    private static String formatCardsCompact(String levelPrefix, List<DevelopmentCard> cards) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < cards.size(); i += CARDS_PER_ROW) {
            if (i == 0) {
                sb.append(levelPrefix).append(": ");
            } else {
                sb.append("    "); // Indentation for continuation rows
            }

            List<String> rowCards = new ArrayList<>();
            for (int j = i; j < Math.min(i + CARDS_PER_ROW, cards.size()); j++) {
                DevelopmentCard card = cards.get(j);
                String pts = card.prestigePoints() == 1 ? "1pt" : card.prestigePoints() + "pts";
                String cardStr = String.format("[%s] %s %s %s",
                        card.id(),
                        formatBonusGemShort(card.bonusGem()),
                        pts,
                        formatCostCompact(card.cost()));
                // Pad to constant width for alignment
                rowCards.add(String.format("%-" + CARD_WIDTH + "s", cardStr));
            }
            sb.append(String.join(" | ", rowCards));
            if (i + CARDS_PER_ROW < cards.size()) {
                sb.append(" |");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static String formatBonusGemShort(Color color) {
        return switch (color) {
            case WHITE -> "WHT";
            case BLUE -> "BLU";
            case GREEN -> "GRN";
            case RED -> "RED";
            case BLACK -> "BLK";
            case GOLD -> "GLD";
        };
    }

    private static String formatPlayersSideBySide(GameState state, List<String> modelNames) {
        List<Player> players = state.players();
        if (players.size() != 2) {
            // Fall back to vertical format for non-2-player games
            return formatPlayersVertical(state, modelNames);
        }

        Player p0 = players.get(0);
        Player p1 = players.get(1);
        boolean p0Current = state.currentPlayerIndex() == 0;
        boolean p1Current = state.currentPlayerIndex() == 1;
        String model0 = extractModelName(modelNames.get(0));
        String model1 = extractModelName(modelNames.get(1));

        StringBuilder sb = new StringBuilder();

        // Header row
        String h0 = String.format("%s (P%d)%s:", model0, p0.id(), p0Current ? " (Current)" : "");
        String h1 = String.format("%s (P%d)%s:", model1, p1.id(), p1Current ? " (Current)" : "");
        sb.append(formatTwoColumns(h0, h1));

        // Score & Cards row
        String s0 = String.format("  Score: %d | Cards: %d", p0.score(), p0.purchasedCards().size());
        String s1 = String.format("  Score: %d | Cards: %d", p1.score(), p1.purchasedCards().size());
        sb.append(formatTwoColumns(s0, s1));

        // Tokens row
        String t0 = "  Tokens: " + formatTokenBankCompact(p0.tokens());
        String t1 = "  Tokens: " + formatTokenBankCompact(p1.tokens());
        sb.append(formatTwoColumns(t0, t1));

        // Bonuses row
        String b0 = "  Bonuses: " + formatBonusesCompact(p0.bonuses());
        String b1 = "  Bonuses: " + formatBonusesCompact(p1.bonuses());
        sb.append(formatTwoColumns(b0, b1));

        // Reserved rows - handle multi-line reserved cards properly
        List<String> reserved0Lines = getReservedLines(p0);
        List<String> reserved1Lines = getReservedLines(p1);
        int maxReservedLines = Math.max(reserved0Lines.size(), reserved1Lines.size());

        for (int i = 0; i < maxReservedLines; i++) {
            String left = i < reserved0Lines.size() ? reserved0Lines.get(i) : "";
            String right = i < reserved1Lines.size() ? reserved1Lines.get(i) : "";
            sb.append(formatTwoColumns(left, right));
        }

        return sb.toString();
    }

    private static List<String> getReservedLines(Player p) {
        List<String> lines = new ArrayList<>();
        if (p.reservedCards().isEmpty()) {
            lines.add("  Reserved: None");
        } else {
            for (int i = 0; i < p.reservedCards().size(); i++) {
                DevelopmentCard card = p.reservedCards().get(i);
                String pts = card.prestigePoints() == 1 ? "1pt" : card.prestigePoints() + "pts";
                String cardStr = String.format("[%s] %s %s %s",
                        card.id(),
                        formatBonusGemShort(card.bonusGem()),
                        pts,
                        formatCostCompact(card.cost()));
                if (i == 0) {
                    lines.add("  Reserved: " + cardStr);
                } else {
                    lines.add("            " + cardStr); // Indentation to align under first card
                }
            }
        }
        return lines;
    }

    private static String formatTwoColumns(String left, String right) {
        String paddedLeft = String.format("%-" + PLAYER_COLUMN_WIDTH + "s", left);
        return paddedLeft + "| " + right + "\n";
    }

    private static String formatPlayersVertical(GameState state, List<String> modelNames) {
        StringBuilder sb = new StringBuilder();
        for (Player p : state.players()) {
            boolean isCurrent = state.players().indexOf(p) == state.currentPlayerIndex();
            String modelName = extractModelName(modelNames.get(p.id()));
            sb.append(String.format("%s (P%d)%s:\n", modelName, p.id(), isCurrent ? " (Current)" : ""));
            sb.append(String.format("  Score: %d | Cards: %d\n", p.score(), p.purchasedCards().size()));
            sb.append(String.format("  Tokens: %s\n", formatTokenBankCompact(p.tokens())));
            sb.append(String.format("  Bonuses: %s\n", formatBonusesCompact(p.bonuses())));
            sb.append(String.format("  Reserved: %s\n", formatReserved(p)));
        }
        return sb.toString();
    }

    private static String formatReserved(Player p) {
        if (p.reservedCards().isEmpty()) {
            return "None";
        }
        List<String> cardStrings = p.reservedCards().stream()
                .map(card -> {
                    String pts = card.prestigePoints() == 1 ? "1pt" : card.prestigePoints() + "pts";
                    return String.format("[%s] %s %s %s",
                            card.id(),
                            formatBonusGemShort(card.bonusGem()),
                            pts,
                            formatCostCompact(card.cost()));
                })
                .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder(cardStrings.get(0));
        for (int i = 1; i < cardStrings.size(); i++) {
            sb.append("\n            ").append(cardStrings.get(i)); // Indentation to align under first card
        }
        return sb.toString();
    }

    private static String formatTokenBankCompact(TokenBank bank) {
        return bank.counts().entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(e -> String.format("%s:%d", formatColorShort(e.getKey()), e.getValue()))
                .collect(Collectors.joining(" "));
    }

    private static String formatBonusesCompact(Map<Color, Integer> bonuses) {
        if (bonuses.isEmpty()) {
            return "{}";
        }
        String inner = bonuses.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(e -> String.format("%s:%d", formatColorShort(e.getKey()), e.getValue()))
                .collect(Collectors.joining(" "));
        return "{" + inner + "}";
    }

    private static String formatColorShort(Color color) {
        return switch (color) {
            case WHITE -> "WHT";
            case BLUE -> "BLU";
            case GREEN -> "GRN";
            case RED -> "RED";
            case BLACK -> "BLK";
            case GOLD -> "GLD";
        };
    }

    /**
     * Formats a player's total budget (tokens + bonuses) excluding colors with zero
     * value.
     */
    public static String formatBudget(Player player) {
        Map<Color, Integer> budget = new java.util.EnumMap<>(Color.class);

        // Add tokens
        for (Map.Entry<Color, Integer> entry : player.tokens().counts().entrySet()) {
            budget.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }

        // Add bonuses
        for (Map.Entry<Color, Integer> entry : player.bonuses().entrySet()) {
            budget.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }

        String inner = budget.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(e -> String.format("%s=%d", formatColorShort(e.getKey()), e.getValue()))
                .collect(Collectors.joining(", "));

        return inner.isEmpty() ? "{}" : "{" + inner + "}";
    }

    private static String formatCostCompact(Map<Color, Integer> cost) {
        return cost.entrySet().stream()
                .map(e -> String.format("%s:%d", formatColorShort(e.getKey()), e.getValue()))
                .collect(Collectors.joining(" "));
    }

    /**
     * Extracts the model name from a full model path.
     * E.g., "anthropic/claude-haiku-4.5" -> "claude-haiku-4.5"
     */
    private static String extractModelName(String fullModelPath) {
        if (fullModelPath == null || fullModelPath.isEmpty()) {
            return "Unknown";
        }
        int slashIndex = fullModelPath.lastIndexOf('/');
        return slashIndex >= 0 ? fullModelPath.substring(slashIndex + 1) : fullModelPath;
    }
}
