package com.openingcoach.core.chess;

import java.util.SplittableRandom;

/**
 * Zobrist hashing: every (piece, square) pair, castling state, en passant file and side to
 * move gets a random 64-bit number; a position's key is the XOR of the numbers that apply.
 * Two move orders that reach the same position (a transposition) get the same key.
 *
 * The random table is generated from a fixed seed so keys are stable across runs; the
 * reference index on disk depends on this.
 */
public final class Zobrist {

    private static final long SEED = 0x5EED_0C0A_C4L;
    private static final long[] PIECE_SQUARE = new long[12 * 64];
    private static final long[] CASTLING = new long[16];
    private static final long[] EP_FILE = new long[8];
    private static final long BLACK_TO_MOVE;

    static {
        SplittableRandom rnd = new SplittableRandom(SEED);
        for (int i = 0; i < PIECE_SQUARE.length; i++) {
            PIECE_SQUARE[i] = rnd.nextLong();
        }
        for (int i = 0; i < CASTLING.length; i++) {
            CASTLING[i] = rnd.nextLong();
        }
        for (int i = 0; i < EP_FILE.length; i++) {
            EP_FILE[i] = rnd.nextLong();
        }
        BLACK_TO_MOVE = rnd.nextLong();
    }

    private Zobrist() {
    }

    static long hash(Board b) {
        long h = 0;
        for (int s = 0; s < 64; s++) {
            int p = b.pieceAt(s);
            if (p != Board.EMPTY) {
                int idx = p > 0 ? p - 1 : 6 + (-p - 1);
                h ^= PIECE_SQUARE[idx * 64 + s];
            }
        }
        h ^= CASTLING[b.castlingRights()];
        int ep = b.epSquare();
        if (ep >= 0 && epCapturePossible(b, ep)) {
            h ^= EP_FILE[Squares.file(ep)];
        }
        if (!b.whiteToMove()) {
            h ^= BLACK_TO_MOVE;
        }
        return h;
    }

    /** En passant only changes the position if a pawn of the side to move can actually capture. */
    private static boolean epCapturePossible(Board b, int ep) {
        int sign = b.whiteToMove() ? 1 : -1;
        int pawnRank = Squares.rank(ep) - sign;
        for (int df : new int[]{-1, 1}) {
            int f = Squares.file(ep) + df;
            if (f >= 0 && f < 8 && pawnRank >= 0 && pawnRank < 8 && b.pieceAt(pawnRank * 8 + f) == sign * Board.PAWN) {
                return true;
            }
        }
        return false;
    }
}
