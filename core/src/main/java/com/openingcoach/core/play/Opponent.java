package com.openingcoach.core.play;

import com.openingcoach.core.Stats;
import com.openingcoach.core.chess.Board;
import com.openingcoach.core.reference.ReferenceBook;
import com.openingcoach.core.reference.ReferenceBook.RefMove;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Chooses the computer's reply. While the position is in the reference data it picks from the
 * moves strong players made, using their win % for the side to move; once the data runs out the
 * {@link Engine} takes over. The level changes how greedy the choice is:
 * <ul>
 *   <li>EASY: any move strong players tried, weighted by how often (often not the best one);
 *       then a shallow search that picks randomly among moves within 1.8 pawns of the best.</li>
 *   <li>MEDIUM: favours high-scoring moves but samples among them; then a 2-ply search,
 *       random among moves within 0.35 pawns of the best.</li>
 *   <li>HARD: the highest-scoring well-tested move; then the deepest search in the time budget.</li>
 * </ul>
 */
public final class Opponent {

    public enum Level { EASY, MEDIUM, HARD }

    /** The chosen move and why: source is "book" or "engine"; games/score/share only for book moves. */
    public record Choice(Board.Move move, String source, String note, Integer games, Double score, Double share) {
    }

    /** Positions with fewer reference games than this are treated as out of book. */
    static final int BOOK_MIN_GAMES = 8;
    /** Pseudo-games at 50% added to every move's record, so a 2-0 move does not look like a sure win. */
    static final int PRIOR = 8;

    private final ReferenceBook book;
    private final Random random;
    private final Engine engine = new Engine();

    public Opponent(ReferenceBook book, Random random) {
        this.book = book;
        this.random = random;
    }

    /** Win % with a prior pulling small samples towards 50%. */
    static double adjusted(Stats s, boolean white) {
        double points = s.winsFor(white) + 0.5 * s.draws();
        return (points + 0.5 * PRIOR) / (s.games() + PRIOR);
    }

    public Choice choose(Board b, Level level) {
        Choice fromBook = fromBook(b, level);
        return fromBook != null ? fromBook : fromEngine(b, level);
    }

    Choice fromBook(Board b, Level level) {
        List<RefMove> refs = book.moves(b.key());
        int total = refs.stream().mapToInt(m -> m.stats().games()).sum();
        if (total < BOOK_MIN_GAMES) {
            return null;
        }
        Map<String, Board.Move> legal = new HashMap<>();
        for (Board.Move m : b.legalMoves()) {
            legal.put(m.san(), m);
        }
        boolean white = b.whiteToMove();
        int minGames = switch (level) {
            case EASY -> 1;
            case MEDIUM -> 3;
            case HARD -> Math.max(3, (int) Math.ceil(total * 0.02));
        };
        List<RefMove> cands = new ArrayList<>();
        double bestAdj = -1;
        for (RefMove r : refs) {
            if (legal.containsKey(r.san()) && r.stats().games() >= minGames) {
                cands.add(r);
                bestAdj = Math.max(bestAdj, adjusted(r.stats(), white));
            }
        }
        if (cands.isEmpty()) {
            return null;
        }
        double[] w = new double[cands.size()];
        for (int i = 0; i < w.length; i++) {
            Stats s = cands.get(i).stats();
            double adj = adjusted(s, white);
            w[i] = switch (level) {
                case EASY -> Math.sqrt(s.games());
                case MEDIUM -> Math.sqrt(s.games()) * Math.exp((adj - bestAdj) / 0.025);
                case HARD -> adj == bestAdj ? 1 : 0;
            };
        }
        RefMove pick = cands.get(sample(w));
        Stats s = pick.stats();
        double share = (double) s.games() / total;
        String note = String.format("From the database: strong players chose this in %d%% of %,d games here and scored %d%% with it.",
                Math.round(share * 100), total, Math.round(s.scoreFor(white) * 100));
        return new Choice(legal.get(pick.san()), "book", note, s.games(), s.scoreFor(white), share);
    }

    Choice fromEngine(Board b, Level level) {
        List<Board.Move> legal = b.legalMoves();
        if (legal.isEmpty()) {
            return null;
        }
        Board.Move move;
        if (level == Level.HARD) {
            move = engine.best(b, 6, 1500).move(); // iterative deepening: as deep as 1.5 s allows
        } else {
            int depth = level == Level.EASY ? 1 : 2;
            int window = level == Level.EASY ? 180 : 35;
            List<Engine.Scored> ranked = engine.rank(b, depth);
            int best = ranked.get(0).score();
            List<Engine.Scored> ok = ranked.stream().filter(s -> s.score() >= best - window).toList();
            move = ok.get(random.nextInt(ok.size())).move();
        }
        String note = String.format("Out of the database now, so it calculated: %,d positions searched.", engine.nodes());
        return new Choice(move, "engine", note, null, null, null);
    }

    private int sample(double[] w) {
        double sum = 0;
        for (double x : w) {
            sum += x;
        }
        double t = random.nextDouble() * sum;
        for (int i = 0; i < w.length; i++) {
            t -= w[i];
            if (t <= 0 && w[i] > 0) {
                return i;
            }
        }
        for (int i = w.length - 1; i >= 0; i--) {
            if (w[i] > 0) {
                return i;
            }
        }
        return 0;
    }
}
