package com.openingcoach.core.pgn;

public enum GameResult {
    WHITE_WIN, DRAW, BLACK_WIN, UNFINISHED;

    public static GameResult parse(String s) {
        if (s == null) {
            return UNFINISHED;
        }
        return switch (s.trim()) {
            case "1-0" -> WHITE_WIN;
            case "0-1" -> BLACK_WIN;
            case "1/2-1/2", "½-½" -> DRAW;
            default -> UNFINISHED;
        };
    }

    /** Points scored by the given side: 1, 0.5 or 0. */
    public double pointsFor(boolean white) {
        return switch (this) {
            case WHITE_WIN -> white ? 1.0 : 0.0;
            case BLACK_WIN -> white ? 0.0 : 1.0;
            case DRAW -> 0.5;
            case UNFINISHED -> throw new IllegalStateException("Unfinished game has no score");
        };
    }
}
