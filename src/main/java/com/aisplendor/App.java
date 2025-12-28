package com.aisplendor;

import com.aisplendor.engine.GameSimulator;

import java.nio.file.Path;

public class App {
    public static void main(String[] args) {
        if (args.length >= 2 && "--resume".equals(args[0])) {
            GameSimulator.resumeGame(Path.of(args[1]));
        } else {
            GameSimulator.initializeGame();
        }
    }
}