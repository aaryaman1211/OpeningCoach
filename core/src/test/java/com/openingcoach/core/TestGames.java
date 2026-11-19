package com.openingcoach.core;

import com.openingcoach.core.chess.Board;
import com.openingcoach.core.chess.IllegalMoveException;
import com.openingcoach.core.pgn.GameResult;
import com.openingcoach.core.pgn.PgnGame;
import com.openingcoach.core.pgn.ReplayedGame;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Test helpers: build replayed games from SAN, and random-but-legal opening games. */
public final class TestGames {

    private static final String[] PLAUSIBLE = {
            "e4", "d4", "c4", "Nf3", "g3", "b3", "f4", "e5", "d5", "c5", "Nf6", "e6", "c6", "d6", "g6",
            "Nc3", "Nc6", "Bc4", "Bb5", "Be2", "Bd3", "Bg2", "Be7", "Bg7", "Bc5", "Bb4", "O-O", "a6", "h6",
            "exd5", "dxe5", "cxd5", "exd4", "cxd4", "Nxd4", "Nxe5", "Qxd5", "Bxc6", "h3", "a3", "Re1", "Qe2"};

    private TestGames() {
    }

    public static ReplayedGame replay(GameResult result, String... sans) {
        Board b = Board.startPosition();
        long[] keys = new long[sans.length + 1];
        keys[0] = b.key();
        List<String> moves = new ArrayList<>();
        for (int i = 0; i < sans.length; i++) {
            moves.add(b.applySan(sans[i]));
            keys[i + 1] = b.key();
        }
        return new ReplayedGame(moves, keys, result, pgn("white", "black", result, moves));
    }

    public static PgnGame pgn(String white, String black, GameResult result, List<String> moves) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("White", white);
        tags.put("Black", black);
        tags.put("Result", switch (result) {
            case WHITE_WIN -> "1-0";
            case BLACK_WIN -> "0-1";
            case DRAW -> "1/2-1/2";
            case UNFINISHED -> "*";
        });
        return new PgnGame(tags, moves);
    }

    /** Random legal games: at each ply try plausible SAN moves in random order, keep the first legal one. */
    public static List<ReplayedGame> randomGames(Random rnd, int count, int plies) {
        List<ReplayedGame> out = new ArrayList<>();
        GameResult[] results = {GameResult.WHITE_WIN, GameResult.DRAW, GameResult.BLACK_WIN};
        while (out.size() < count) {
            Board b = Board.startPosition();
            List<String> moves = new ArrayList<>();
            List<Long> keys = new ArrayList<>();
            keys.add(b.key());
            for (int p = 0; p < plies; p++) {
                String played = null;
                for (int attempt = 0; attempt < 30 && played == null; attempt++) {
                    String cand = PLAUSIBLE[rnd.nextInt(PLAUSIBLE.length)];
                    Board trial = b.copy();
                    try {
                        trial.applySan(cand);
                        b.applySan(cand);
                        played = cand;
                    } catch (IllegalMoveException ignored) {
                        // try another
                    }
                }
                if (played == null) {
                    break;
                }
                moves.add(played);
                keys.add(b.key());
            }
            if (moves.isEmpty()) {
                continue;
            }
            long[] k = keys.stream().mapToLong(Long::longValue).toArray();
            GameResult r = results[rnd.nextInt(3)];
            out.add(new ReplayedGame(moves, k, r, pgn("white", "black", r, moves)));
        }
        return out;
    }
}
