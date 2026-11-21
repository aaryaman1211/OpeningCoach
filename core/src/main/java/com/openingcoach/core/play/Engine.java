package com.openingcoach.core.play;

import com.openingcoach.core.chess.Board;
import com.openingcoach.core.chess.Squares;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A small chess engine used once a game leaves the reference data: depth-limited negamax with
 * alpha-beta pruning, a capture-only quiescence search at the leaves, and MVV-LVA move ordering.
 * The evaluation is material plus simple positional terms (centralisation, pawn advance,
 * king safety, bishop pair). Scores are in centipawns from the side to move's point of view.
 *
 * <p>Alpha-beta returns the same value as plain minimax but skips subtrees that cannot change
 * the result; with good ordering it searches roughly b^(d/2) nodes instead of b^d.
 */
public final class Engine {

    public static final int MATE = 100_000;
    static final int[] VALUE = {0, 100, 320, 330, 500, 900, 0};
    private static final int INF = 1_000_000;
    private static final int MAX_QDEPTH = 6;

    /** A root move with its score (centipawns for the side to move; ±MATE-ish for forced mates). */
    public record Scored(Board.Move move, int score) {
    }

    private long nodes;
    private long deadline = Long.MAX_VALUE;
    private boolean stopped;

    public long nodes() {
        return nodes;
    }

    /**
     * Best move by iterative deepening up to {@code maxDepth} plies, stopping early when the
     * time budget runs out (the last completed depth is used). Null if there are no legal moves.
     */
    public Scored best(Board b, int maxDepth, long timeMs) {
        List<Board.Move> moves = b.legalMoves();
        if (moves.isEmpty()) {
            return null;
        }
        nodes = 0;
        stopped = false;
        deadline = System.currentTimeMillis() + timeMs;
        order(b, moves);
        Scored bestSoFar = new Scored(moves.get(0), 0);
        for (int depth = 1; depth <= maxDepth; depth++) {
            int alpha = -INF;
            Board.Move bestMove = null;
            for (Board.Move m : moves) {
                int s = -negamax(b.after(m), depth - 1, -INF, -alpha, 1);
                if (stopped) {
                    break;
                }
                if (s > alpha) {
                    alpha = s;
                    bestMove = m;
                }
            }
            if (stopped || bestMove == null) {
                break;
            }
            bestSoFar = new Scored(bestMove, alpha);
            // search the best move first at the next depth: more cut-offs
            moves.remove(bestMove);
            moves.add(0, bestMove);
            if (Math.abs(alpha) > MATE - 100) {
                break; // a forced mate was found; deeper search cannot improve it
            }
        }
        deadline = Long.MAX_VALUE;
        return bestSoFar;
    }

    /** Every legal move with an exact score from a {@code depth}-ply search, best first. */
    public List<Scored> rank(Board b, int depth) {
        nodes = 0;
        stopped = false;
        deadline = Long.MAX_VALUE;
        List<Scored> out = new ArrayList<>();
        for (Board.Move m : b.legalMoves()) {
            out.add(new Scored(m, -negamax(b.after(m), depth - 1, -INF, INF, 1)));
        }
        out.sort(Comparator.comparingInt(Scored::score).reversed());
        return out;
    }

    private int negamax(Board b, int depth, int alpha, int beta, int ply) {
        if ((++nodes & 1023) == 0 && System.currentTimeMillis() > deadline) {
            stopped = true;
        }
        if (stopped) {
            return 0;
        }
        List<Board.Move> moves = b.legalMoves();
        if (moves.isEmpty()) {
            return b.inCheck() ? -(MATE - ply) : 0; // checkmate, or stalemate
        }
        if (b.halfmoveClock() >= 100) {
            return 0;
        }
        if (depth <= 0) {
            return quiesce(b, alpha, beta, 0, moves);
        }
        order(b, moves);
        for (Board.Move m : moves) {
            int s = -negamax(b.after(m), depth - 1, -beta, -alpha, ply + 1);
            if (s >= beta) {
                return beta; // the opponent will avoid this line: cut off
            }
            if (s > alpha) {
                alpha = s;
            }
        }
        return alpha;
    }

    /** Only captures and promotions, so the evaluation is not taken in the middle of an exchange. */
    private int quiesce(Board b, int alpha, int beta, int qdepth, List<Board.Move> moves) {
        int stand = evaluate(b);
        if (stand >= beta) {
            return beta;
        }
        if (stand > alpha) {
            alpha = stand;
        }
        if (qdepth >= MAX_QDEPTH) {
            return alpha;
        }
        List<Board.Move> tactical = new ArrayList<>();
        for (Board.Move m : moves) {
            if (b.pieceAt(m.to()) != Board.EMPTY || m.promotion() != Board.EMPTY) {
                tactical.add(m);
            }
        }
        order(b, tactical);
        for (Board.Move m : tactical) {
            Board next = b.after(m);
            nodes++;
            List<Board.Move> replies = next.legalMoves();
            int s = replies.isEmpty() ? (next.inCheck() ? MATE - 1 : 0) : -quiesce(next, -beta, -alpha, qdepth + 1, replies);
            if (s >= beta) {
                return beta;
            }
            if (s > alpha) {
                alpha = s;
            }
        }
        return alpha;
    }

    /** Captures first, most valuable victim / least valuable attacker; then promotions. */
    static void order(Board b, List<Board.Move> moves) {
        moves.sort(Comparator.comparingInt((Board.Move m) -> {
            int victim = Math.abs(b.pieceAt(m.to()));
            int attacker = Math.abs(b.pieceAt(m.from()));
            int key = victim == Board.EMPTY ? 0 : 10 * VALUE[victim] - VALUE[attacker] + 10_000;
            if (m.promotion() != Board.EMPTY) {
                key += VALUE[m.promotion()];
            }
            return -key;
        }));
    }

    /** Static evaluation in centipawns for the side to move. */
    public static int evaluate(Board b) {
        int score = 0;
        int whiteBishops = 0, blackBishops = 0, whiteHeavy = 0, blackHeavy = 0;
        for (int s = 0; s < 64; s++) {
            int p = b.pieceAt(s);
            if (p != Board.EMPTY && Math.abs(p) != Board.PAWN && Math.abs(p) != Board.KING) {
                if (p > 0) {
                    whiteHeavy += VALUE[p];
                } else {
                    blackHeavy += VALUE[-p];
                }
            }
        }
        boolean endgame = whiteHeavy <= 1300 && blackHeavy <= 1300;
        for (int s = 0; s < 64; s++) {
            int p = b.pieceAt(s);
            if (p == Board.EMPTY) {
                continue;
            }
            boolean white = p > 0;
            int type = Math.abs(p);
            int v = VALUE[type] + positional(type, s, white, endgame);
            if (type == Board.BISHOP) {
                if (white) {
                    whiteBishops++;
                } else {
                    blackBishops++;
                }
            }
            score += white ? v : -v;
        }
        if (whiteBishops >= 2) {
            score += 30;
        }
        if (blackBishops >= 2) {
            score -= 30;
        }
        return b.whiteToMove() ? score : -score;
    }

    /** Positional bonus for one piece, from its owner's point of view. */
    static int positional(int type, int square, boolean white, boolean endgame) {
        int f = Squares.file(square);
        int r = white ? Squares.rank(square) : 7 - Squares.rank(square); // rank from the owner's side
        double centre = 3.5 - Math.max(Math.abs(f - 3.5), Math.abs(Squares.rank(square) - 3.5)); // 0.5 edge .. 3.5 centre
        return switch (type) {
            case Board.PAWN -> {
                int bonus = (r - 1) * (endgame ? 14 : 6);
                if ((f == 3 || f == 4) && (r == 3 || r == 4)) {
                    bonus += 20; // pawns on d4/e4 (d5/e5 for Black) hold the centre
                }
                yield bonus;
            }
            case Board.KNIGHT -> (int) (centre * 12) - 22 - (r == 0 ? 10 : 0);
            case Board.BISHOP -> (int) (centre * 6) - (r == 0 ? 12 : 0);
            case Board.ROOK -> (r == 6 ? 20 : 0) + ((f == 3 || f == 4) ? 5 : 0);
            case Board.QUEEN -> (int) (centre * 3);
            case Board.KING -> endgame ? (int) (centre * 10) - 15
                    : (r == 0 ? 15 : -10 * r) + ((f <= 2 || f >= 6) ? 20 : -15);
            default -> 0;
        };
    }
}
