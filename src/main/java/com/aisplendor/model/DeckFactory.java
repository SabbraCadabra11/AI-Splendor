package com.aisplendor.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Factory for creating the standard Splendor decks and nobles.
 */
public class DeckFactory {

    public static Map<CardLevel, Queue<DevelopmentCard>> createStandardDecks() {
        Map<CardLevel, List<DevelopmentCard>> cardsByLevel = new EnumMap<>(CardLevel.class);
        for (CardLevel level : CardLevel.values()) {
            cardsByLevel.put(level, new ArrayList<>());
        }

        try (InputStream is = DeckFactory.class.getResourceAsStream("/development_cards.csv");
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (lineNum == 1)
                    continue; // Skip header

                // Handle potential Windows line endings
                line = line.replace("\r", "");
                String[] parts = line.split(",");
                if (parts.length < 8)
                    continue;

                int levelInt = Integer.parseInt(parts[0].trim());
                CardLevel level = switch (levelInt) {
                    case 1 -> CardLevel.LEVEL_1;
                    case 2 -> CardLevel.LEVEL_2;
                    case 3 -> CardLevel.LEVEL_3;
                    default -> throw new IllegalArgumentException("Invalid card level: " + levelInt);
                };

                Color bonusGem = Color.valueOf(parts[1].trim().toUpperCase());
                int prestigePoints = Integer.parseInt(parts[2].trim());

                Map<Color, Integer> cost = new EnumMap<>(Color.class);
                int blackCost = Integer.parseInt(parts[3].trim());
                int blueCost = Integer.parseInt(parts[4].trim());
                int greenCost = Integer.parseInt(parts[5].trim());
                int redCost = Integer.parseInt(parts[6].trim());
                int whiteCost = Integer.parseInt(parts[7].trim());

                if (blackCost > 0)
                    cost.put(Color.BLACK, blackCost);
                if (blueCost > 0)
                    cost.put(Color.BLUE, blueCost);
                if (greenCost > 0)
                    cost.put(Color.GREEN, greenCost);
                if (redCost > 0)
                    cost.put(Color.RED, redCost);
                if (whiteCost > 0)
                    cost.put(Color.WHITE, whiteCost);

                String id = String.format("L%d_%d", levelInt, lineNum - 1);
                DevelopmentCard card = new DevelopmentCard(id, level, bonusGem, prestigePoints, cost);
                cardsByLevel.get(level).add(card);
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to load development cards from CSV", e);
        }

        Map<CardLevel, Queue<DevelopmentCard>> decks = new EnumMap<>(CardLevel.class);
        for (CardLevel level : CardLevel.values()) {
            List<DevelopmentCard> cards = cardsByLevel.get(level);
            Collections.shuffle(cards);
            decks.put(level, new LinkedList<>(cards));
        }

        return decks;
    }

    public static List<NobleTile> createStandardNobles() {
        List<NobleTile> nobles = new ArrayList<>();
        nobles.add(new NobleTile("N1", 3, Map.of(Color.WHITE, 4, Color.BLUE, 4)));
        nobles.add(new NobleTile("N2", 3, Map.of(Color.GREEN, 4, Color.RED, 4)));
        nobles.add(new NobleTile("N3", 3, Map.of(Color.BLACK, 4, Color.WHITE, 4)));
        nobles.add(new NobleTile("N4", 3, Map.of(Color.BLUE, 4, Color.GREEN, 4)));
        nobles.add(new NobleTile("N5", 3, Map.of(Color.RED, 4, Color.BLACK, 4)));
        nobles.add(new NobleTile("N6", 3, Map.of(Color.WHITE, 3, Color.BLUE, 3, Color.BLACK, 3)));
        nobles.add(new NobleTile("N7", 3, Map.of(Color.GREEN, 3, Color.RED, 3, Color.BLACK, 3)));
        nobles.add(new NobleTile("N8", 3, Map.of(Color.WHITE, 3, Color.BLUE, 3, Color.GREEN, 3)));
        nobles.add(new NobleTile("N9", 3, Map.of(Color.RED, 3, Color.GREEN, 3, Color.BLUE, 3)));
        nobles.add(new NobleTile("N10", 3, Map.of(Color.WHITE, 3, Color.RED, 3, Color.BLACK, 3)));
        return nobles;
    }
}
