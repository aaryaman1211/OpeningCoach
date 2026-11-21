package com.openingcoach.core.play;

import com.openingcoach.core.Stats;
import com.openingcoach.core.chess.Board;
import com.openingcoach.core.chess.Squares;
import com.openingcoach.core.reference.ReferenceBook;
import com.openingcoach.core.reference.ReferenceBook.RefMove;

import java.util.ArrayList;
import java.util.List;

/**
 * Grades a move the player just made and explains it, without ever naming the better move.
 *
 * <p>In known positions the grade comes from the reference games: the move's win % against the
 * best well-tested alternative. Everywhere (and to catch tactics the statistics hide) a 3-ply
 * {@link Engine} search measures how much the move loses. The explanation is facts about the
 * player's own move (its record, pieces it leaves loose, threats it allows) plus graded hints
 * about the better move: first the idea, then which piece, then where on the board.
 */
public final class MoveCoach {

    /** best, good, ok, dubious, mistake or blunder; hints are empty unless the move should be reconsidered. */
    public record Review(String verdict, String headline, List<String> facts, List<String> hints,
                         Integer positionGames, Integer yourGames, Double yourScore, Double bestScore, Integer centipawnLoss) {

        public boolean reconsider() {
            return verdict.equals("dubious") || verdict.equals("mistake") || verdict.equals("blunder");
        }
    }

    private static final String[] PIECE = {"", "pawn", "knight", "bishop", "rook", "queen", "king"};
    private static final List<String> ORDER = List.of("best", "good", "ok", "dubious", "mistake", "blunder");

    private final ReferenceBook book;
    private final Engine engine = new Engine();

    public MoveCoach(ReferenceBook book) {
        this.book = book;
    }

    public Review review(Board before, Board.Move played, int plyNumber) {
        boolean white = before.whiteToMove();
        Board after = before.after(played);
        List<String> facts = new ArrayList<>();

        // ---- what strong players did here
        List<RefMove> refs = book.moves(before.key());
        int total = refs.stream().mapToInt(m -> m.stats().games()).sum();
        RefMove yours = refs.stream().filter(r -> r.san().equals(played.san())).findFirst().orElse(null);
        RefMove bestRef = null;
        double bestAdj = -1;
        int tested = Math.max(5, (int) Math.ceil(total * 0.01)); // "well tested": at least 1% of the games here
        for (RefMove r : refs) {
            if (r.stats().games() >= tested) {
                double a = Opponent.adjusted(r.stats(), white);
                if (a > bestAdj) {
                    bestAdj = a;
                    bestRef = r;
                }
            }
        }

        // ---- what the search says
        // 3 plies: your move, their reply, your answer; then captures. An even depth would stop right
        // after their reply and miss what they threaten (a check that only delays a loss looks good).
        List<Engine.Scored> ranked = engine.rank(before, 3);
        int bestCp = ranked.get(0).score();
        int yourCp = ranked.stream().filter(s -> s.move().equals(played)).mapToInt(Engine.Scored::score).findFirst().orElse(bestCp);
        int loss = Math.max(0, bestCp - yourCp);

        String verdict;
        boolean bookPhase = total >= 30 && bestRef != null;
        if (bookPhase && yours != null && yours.stats().games() >= tested) {
            double d = bestAdj - Opponent.adjusted(yours.stats(), white);
            // win % alone is noisy: a main-line move (15%+ of games) is never marked down on statistics,
            // and only large samples on both sides can make a move "dubious" or a "mistake"
            boolean mainline = (double) yours.stats().games() / total >= 0.15;
            boolean solid = yours.stats().games() >= 30 && bestRef.stats().games() >= 30;
            if (yours == bestRef || d <= 0.015) {
                verdict = "best";
            } else if (d <= 0.04 || (mainline && d <= 0.10)) {
                verdict = "good";
            } else if (d <= 0.08 || mainline || !solid) {
                verdict = "ok";
            } else {
                verdict = d <= 0.14 ? "dubious" : "mistake";
            }
        } else {
            verdict = loss <= 15 ? "best" : loss <= 40 ? "good" : loss <= 90 ? "ok" : loss <= 180 ? "dubious" : loss <= 350 ? "mistake" : "blunder";
            if (bookPhase && total >= 100) {
                // a well-known position where strong players (almost) never chose this
                verdict = worse(verdict, loss > 40 ? "dubious" : "ok");
            }
        }
        if (loss >= 250) {
            verdict = worse(verdict, loss >= 400 ? "blunder" : "mistake"); // tactics beat statistics
        }

        // ---- facts about the player's move
        if (total >= 30) {
            facts.add(String.format("Strong players reached this position in %,d games.", total));
            if (yours != null && yours.stats().games() >= tested) {
                facts.add(String.format("Your move was played in %,d of them and scored %d%% for your side.",
                        yours.stats().games(), pct(yours.stats(), white)));
            } else if (yours != null && yours.stats().games() >= 3) {
                facts.add(String.format("Your move was played in only %,d of them: too few games to trust its result.",
                        yours.stats().games()));
            } else {
                facts.add("Strong players almost never chose your move here.");
            }
            if (bestRef != null && yours != bestRef && ORDER.indexOf(verdict) >= ORDER.indexOf("ok")) {
                facts.add(String.format("Their most successful well-tested choice scores %d%%.", pct(bestRef.stats(), white)));
            }
        }
        String loose = loosePiece(after, white);
        if (loose != null && loss >= 120) {
            facts.add(loose);
        }
        if (allowsMate(after)) {
            facts.add("This allows checkmate on the very next move.");
            verdict = "blunder";
        }

        List<String> hints = new ArrayList<>();
        Board.Move fromData = bookPhase && bestRef != null ? findLegal(before, bestRef.san()) : null;
        int dataCp = fromData == null ? Integer.MIN_VALUE
                : ranked.stream().filter(s -> s.move().equals(fromData)).mapToInt(Engine.Scored::score).findFirst().orElse(bestCp);
        // statistics disagree with a clear tactic: trust the tactic
        Board.Move target = fromData != null && dataCp >= bestCp - 150 ? fromData : ranked.get(0).move();
        if (target.equals(played)) {
            target = ranked.stream().map(Engine.Scored::move).filter(x -> !x.equals(played)).findFirst().orElse(played);
        }
        Review draft = new Review(verdict, "", facts, hints, null, null, null, null, null);
        if (draft.reconsider()) {
            hints.addAll(hintsFor(before, target, ranked.get(0).score(), plyNumber, loose != null && loss >= 120));
        }
        return new Review(verdict, headline(verdict), facts, hints,
                total == 0 ? null : total,
                yours == null ? null : yours.stats().games(),
                yours == null || yours.stats().games() < tested ? null : yours.stats().scoreFor(white),
                bestRef == null ? null : bestRef.stats().scoreFor(white),
                loss);
    }

    private static String worse(String a, String b) {
        return ORDER.indexOf(a) >= ORDER.indexOf(b) ? a : b;
    }

    private static int pct(Stats s, boolean white) {
        return (int) Math.round(s.scoreFor(white) * 100);
    }

    private static String headline(String verdict) {
        return switch (verdict) {
            case "best" -> "Excellent. That is what the strongest players choose.";
            case "good" -> "Good move.";
            case "ok" -> "Playable, but there was something more useful.";
            case "dubious" -> "Hmm. That makes your position harder.";
            case "mistake" -> "That is a mistake.";
            default -> "That is a blunder.";
        };
    }

    /** The most valuable of the player's pieces the opponent can now win, described in words. */
    static String loosePiece(Board after, boolean playerWhite) {
        int bestGain = 0;
        String text = null;
        for (Board.Move m : after.legalMoves()) {
            int victim = Math.abs(after.pieceAt(m.to()));
            if (victim == Board.EMPTY || victim == Board.KING) {
                continue;
            }
            int attacker = Math.abs(after.pieceAt(m.from()));
            boolean defended = after.isAttacked(m.to(), playerWhite);
            int gain = defended ? Engine.VALUE[victim] - Engine.VALUE[attacker] : Engine.VALUE[victim];
            if (gain > bestGain) {
                bestGain = gain;
                text = defended
                        ? String.format("Your %s on %s can be taken by a %s, which wins material even though it is defended.",
                        PIECE[victim], Squares.name(m.to()), PIECE[attacker])
                        : String.format("Your %s on %s is now undefended and can be taken for free.", PIECE[victim], Squares.name(m.to()));
            }
        }
        return bestGain >= 100 ? text : null;
    }

    static boolean allowsMate(Board after) {
        for (Board.Move m : after.legalMoves()) {
            Board next = after.after(m);
            if (next.inCheck() && next.legalMoves().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static Board.Move findLegal(Board b, String san) {
        return b.legalMoves().stream().filter(m -> m.san().equals(san)).findFirst().orElse(null);
    }

    /** Three hints about {@code target}, from general to specific, never naming the move. */
    static List<String> hintsFor(Board before, Board.Move target, int bestScore, int plyNumber, boolean playerLeftPieceLoose) {
        List<String> hints = new ArrayList<>();
        int type = Math.abs(before.pieceAt(target.from()));
        int victim = Math.abs(before.pieceAt(target.to()));
        boolean castle = target.san().startsWith("O-O");
        Board next = before.after(target);
        boolean check = next.inCheck();
        boolean white = before.whiteToMove();
        int homeRank = white ? 0 : 7;

        String idea;
        if (bestScore > Engine.MATE - 100) {
            idea = "Idea: there is a forced win here. Look at every check you can give.";
        } else if (playerLeftPieceLoose) {
            idea = "Idea: before anything else, keep your pieces protected. Look at what your move left hanging.";
        } else if (castle) {
            idea = "Idea: king safety. Your king is still in the centre, where lines can open against it.";
        } else if (victim != Board.EMPTY && (!before.isAttacked(target.to(), !white)
                || Engine.VALUE[victim] >= Engine.VALUE[type])) {
            idea = "Idea: material. One of their pieces is not properly protected.";
        } else if (check) {
            idea = "Idea: forcing moves. A check here gains time.";
        } else if (type == Board.PAWN && plyNumber < 16 && (Squares.file(target.to()) == 3 || Squares.file(target.to()) == 4)) {
            idea = "Idea: the centre. A pawn can claim more space in the middle of the board.";
        } else if (type == Board.PAWN && plyNumber < 16) {
            idea = "Idea: space. A pawn move can open lines for your pieces and take room from theirs.";
        } else if ((type == Board.KNIGHT || type == Board.BISHOP) && Squares.rank(target.from()) == homeRank) {
            idea = "Idea: development. A piece that has not moved yet wants to join the game.";
        } else if (plyNumber < 20) {
            idea = "Idea: think about which of your pieces is doing the least, and give it a better square.";
        } else {
            idea = "Idea: look at what your opponent wants to do next, and the move that stops it while improving a piece.";
        }
        hints.add(idea);
        hints.add(castle ? "Piece: it involves your king and a rook." : "Piece: it is a " + PIECE[type] + " move.");
        int f = Squares.file(castle ? (target.san().equals("O-O") ? 6 : 2) : target.to());
        String side = f <= 2 ? "on the queenside" : f >= 5 ? "on the kingside" : "in the centre";
        String kind = victim != Board.EMPTY ? "it is a capture" : check ? "it gives check" : "it is a quiet move";
        hints.add("Where: " + kind + ", and it lands " + side + ".");
        return hints;
    }
}
