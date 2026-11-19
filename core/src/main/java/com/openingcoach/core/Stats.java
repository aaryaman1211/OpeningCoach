package com.openingcoach.core;

import com.openingcoach.core.pgn.GameResult;

/** Win/draw/loss counts from White's point of view, with helpers for either side's score. */
public record Stats(int whiteWins, int draws, int blackWins) {

    public static final Stats EMPTY = new Stats(0, 0, 0);

    public Stats {
        if (whiteWins < 0 || draws < 0 || blackWins < 0) {
            throw new IllegalArgumentException("Counts cannot be negative");
        }
    }

    public int games() {
        return whiteWins + draws + blackWins;
    }

    /** Score (0..1) for the given side, or NaN when there are no games. */
    public double scoreFor(boolean white) {
        int g = games();
        if (g == 0) {
            return Double.NaN;
        }
        double wins = white ? whiteWins : blackWins;
        return (wins + 0.5 * draws) / g;
    }

    public int winsFor(boolean white) {
        return white ? whiteWins : blackWins;
    }

    public int lossesFor(boolean white) {
        return white ? blackWins : whiteWins;
    }

    public Stats plus(GameResult r) {
        return switch (r) {
            case WHITE_WIN -> new Stats(whiteWins + 1, draws, blackWins);
            case DRAW -> new Stats(whiteWins, draws + 1, blackWins);
            case BLACK_WIN -> new Stats(whiteWins, draws, blackWins + 1);
            case UNFINISHED -> this;
        };
    }
}
