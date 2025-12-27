package com.aisplendor.util;

import com.aisplendor.model.*;
import java.util.Map;
import java.util.stream.Collectors;

public class GameStateFormatter {

    public static String format(GameState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n======================================================\n");
        sb.append(String.format("TURN %d | Current Player: %d\n", state.turnNumber(), state.currentPlayerIndex()));
        sb.append("======================================================\n");

        // Board Section
        sb.append("\n[BOARD BANK]\n");
        sb.append(formatTokenBank(state.board().availableTokens()));
        sb.append("\n");

        sb.append("\n[FACE-UP CARDS]\n");
        for (CardLevel level : CardLevel.values()) {
            sb.append(String.format("Level %s:\n", level));
            for (DevelopmentCard card : state.board().faceUpCards().get(level)) {
                sb.append(String.format("  [%s] %-5s | Pts: %d | Cost: %s\n",
                        card.id(), card.bonusGem(), card.prestigePoints(), formatCost(card.cost())));
            }
        }

        // Players Section
        sb.append("\n[PLAYERS]\n");
        for (Player p : state.players()) {
            boolean isCurrent = state.players().indexOf(p) == state.currentPlayerIndex();
            sb.append(String.format("Player %d%s:\n", p.id(), isCurrent ? " (Current)" : ""));
            sb.append(String.format("  Score: %d\n", p.score()));
            sb.append(String.format("  Tokens: %s\n", formatTokenBank(p.tokens())));
            sb.append(String.format("  Bonuses: %s\n", p.bonuses()));

            sb.append("  Purchased (Top 5): ");
            String purchased = p.purchasedCards().stream()
                    .limit(5)
                    .map(DevelopmentCard::id)
                    .collect(Collectors.joining(", "));
            sb.append(purchased.isEmpty() ? "None" : purchased + (p.purchasedCards().size() > 5 ? "..." : ""));
            sb.append("\n");

            sb.append("  Reserved: ");
            String reserved = p.reservedCards().stream()
                    .map(DevelopmentCard::id)
                    .collect(Collectors.joining(", "));
            sb.append(reserved.isEmpty() ? "None" : reserved);
            sb.append("\n");
        }
        sb.append("======================================================\n");
        return sb.toString();
    }

    private static String formatTokenBank(TokenBank bank) {
        return bank.counts().entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(e -> String.format("%s:%d", e.getKey(), e.getValue()))
                .collect(Collectors.joining(", "));
    }

    private static String formatCost(Map<Color, Integer> cost) {
        return cost.entrySet().stream()
                .map(e -> String.format("%s:%d", e.getKey(), e.getValue()))
                .collect(Collectors.joining(" "));
    }
}
