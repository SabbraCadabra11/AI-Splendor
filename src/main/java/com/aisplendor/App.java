package com.aisplendor;

import com.aisplendor.engine.GameSimulator;

import java.nio.file.Path;

public class App {
    public static void main(String[] args) {
        if (args.length >= 2 && "--resume".equals(args[0])) {
            GameSimulator.resumeGame(Path.of(args[1]));
        } else if (args.length >= 1 && !args[0].startsWith("--")) {
            // Properties file path provided as argument
            GameSimulator.initializeGame(Path.of(args[0]));
        } else {
            // No arguments - use default classpath properties
            GameSimulator.initializeGame(null);
        }
    }
}