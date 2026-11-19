package com.openingcoach.core.pgn;

import com.openingcoach.core.chess.Board;
import com.openingcoach.core.chess.IllegalMoveException;

import java.util.ArrayList;
import java.util.List;

/** Replays PGN games on a {@link Board}, producing SAN + position keys for the opening phase. */
public final class GameReplayer {

    /** Why a game could not be used. */
    public enum SkipReason { NON_STANDARD_START, UNFINISHED, NO_MOVES, ILLEGAL_MOVE }

    /** Either a replayed game or the reason it was skipped. */
    public record Outcome(ReplayedGame game, SkipReason skipReason, String detail) {
        public boolean ok() {
            return game != null;
        }
    }

    private GameReplayer() {
    }

    public static Outcome replay(PgnGame game, int maxPly) {
        if (maxPly <= 0) {
            throw new IllegalArgumentException("maxPly must be positive");
        }
        if (!game.isStandardStart()) {
            return new Outcome(null, SkipReason.NON_STANDARD_START, game.tag("Variant"));
        }
        GameResult result = game.result();
        if (result == GameResult.UNFINISHED) {
            return new Outcome(null, SkipReason.UNFINISHED, game.tag("Result"));
        }
        if (game.sanMoves().isEmpty()) {
            return new Outcome(null, SkipReason.NO_MOVES, "");
        }
        Board board = Board.startPosition();
        int plies = Math.min(maxPly, game.sanMoves().size());
        List<String> sans = new ArrayList<>(plies);
        long[] keys = new long[plies + 1];
        keys[0] = board.key();
        for (int i = 0; i < plies; i++) {
            try {
                sans.add(board.applySan(game.sanMoves().get(i)));
            } catch (IllegalMoveException e) {
                return new Outcome(null, SkipReason.ILLEGAL_MOVE, "ply " + (i + 1) + ": " + e.getMessage());
            }
            keys[i + 1] = board.key();
        }
        return new Outcome(new ReplayedGame(sans, keys, result, game), null, null);
    }

    /** Replays a path of SAN moves from the start position; used to draw the board for a tree node. */
    public static Board boardAfter(List<String> sanPath) {
        Board b = Board.startPosition();
        for (String san : sanPath) {
            b.applySan(san);
        }
        return b;
    }
}
