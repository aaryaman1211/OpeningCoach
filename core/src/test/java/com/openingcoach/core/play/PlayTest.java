package com.openingcoach.core.play;

import com.openingcoach.core.Stats;
import com.openingcoach.core.chess.Board;
import com.openingcoach.core.reference.ReferenceBook;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The opponent's choices by level and the coach's grading, against a small hand-made book. */
class PlayTest {

    /** Start position: e4 scores best for White, d4 is fine, a3 is rare and bad. */
    static final class FakeBook implements ReferenceBook {
        final Map<Long, List<RefMove>> moves = new HashMap<>();

        FakeBook() {
            Board s = Board.startPosition();
            moves.put(s.key(), List.of(
                    new RefMove("e4", 0, new Stats(560, 100, 340)),   // 61% for White, 1000 games
                    new RefMove("d4", 0, new Stats(480, 120, 400)),   // 54%
                    new RefMove("Nf3", 0, new Stats(100, 20, 80)),    // 55%
                    new RefMove("a3", 0, new Stats(8, 2, 30))));      // 22.5%
        }

        @Override
        public Optional<Stats> position(long key) {
            return moves.containsKey(key) ? Optional.of(Stats.EMPTY) : Optional.empty();
        }

        @Override
        public List<RefMove> moves(long key) {
            return moves.getOrDefault(key, List.of());
        }
    }

    private static Board.Move legal(Board b, String san) {
        return b.legalMoves().stream().filter(m -> m.san().equals(san)).findFirst().orElseThrow();
    }

    @Test
    void hardPlaysTheHighestScoringBookMove() {
        Opponent o = new Opponent(new FakeBook(), new Random(1));
        for (int i = 0; i < 20; i++) {
            Opponent.Choice c = o.choose(Board.startPosition(), Opponent.Level.HARD);
            assertEquals("e4", c.move().san());
            assertEquals("book", c.source());
        }
    }

    @Test
    void easyVariesAndSometimesPicksWeakerMoves() {
        Opponent o = new Opponent(new FakeBook(), new Random(7));
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            seen.add(o.choose(Board.startPosition(), Opponent.Level.EASY).move().san());
        }
        assertTrue(seen.size() >= 3, "easy should spread its choices: " + seen);
        assertTrue(seen.contains("a3") || seen.contains("d4"));
    }

    @Test
    void mediumPrefersGoodMovesButIsNotFixed() {
        Opponent o = new Opponent(new FakeBook(), new Random(3));
        Map<String, Integer> count = new HashMap<>();
        for (int i = 0; i < 400; i++) {
            count.merge(o.choose(Board.startPosition(), Opponent.Level.MEDIUM).move().san(), 1, Integer::sum);
        }
        assertTrue(count.getOrDefault("e4", 0) > 200, count.toString());
        assertTrue(count.getOrDefault("a3", 0) < 5, count.toString());
    }

    @Test
    void outOfBookTheEngineAnswers() {
        Opponent o = new Opponent(new FakeBook(), new Random(1));
        Board b = Board.startPosition();
        b.applySan("e4");
        Opponent.Choice c = o.choose(b, Opponent.Level.MEDIUM);
        assertEquals("engine", c.source());
        assertTrue(b.legalMoves().contains(c.move()));
    }

    @Test
    void coachGradesBookMovesByWinRate() {
        MoveCoach coach = new MoveCoach(new FakeBook());
        Board s = Board.startPosition();
        MoveCoach.Review best = coach.review(s, legal(s, "e4"), 0);
        assertEquals("best", best.verdict());
        assertTrue(best.hints().isEmpty());
        MoveCoach.Review bad = coach.review(s, legal(s, "a3"), 0);
        assertTrue(bad.reconsider(), bad.verdict());
        assertEquals(3, bad.hints().size());
        // d4 scores less than e4 but is a main line (40% of games): never marked down on statistics alone
        MoveCoach.Review mainline = coach.review(s, legal(s, "d4"), 0);
        assertEquals("good", mainline.verdict());
    }

    @Test
    void hintsNeverNameTheMove() {
        MoveCoach coach = new MoveCoach(new FakeBook());
        Board s = Board.startPosition();
        MoveCoach.Review bad = coach.review(s, legal(s, "a3"), 0);
        for (String h : bad.hints()) {
            assertFalse(h.contains("e4") || h.contains("d4") || h.contains("Nf3"), h);
        }
        assertTrue(bad.hints().get(1).contains("pawn"), bad.hints().toString()); // the better move is a pawn move
    }

    @Test
    void coachSpotsAHangingPieceAndAMateThreat() {
        MoveCoach coach = new MoveCoach(new FakeBook());
        // Black's queen stands on g5, attacked by the knight on f3
        Board b = Board.fromFen("rnb1kbnr/pppp1ppp/8/4p1q1/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 3 3");
        // ...a6 ignores the threat and leaves the queen hanging
        MoveCoach.Review r = coach.review(b, legal(b, "a6"), 5);
        assertEquals("blunder", r.verdict());
        assertTrue(r.facts().stream().anyMatch(f -> f.contains("queen")), r.facts().toString());

        Board fools = Board.startPosition();
        for (String m : List.of("f3", "e5")) {
            fools.applySan(m);
        }
        MoveCoach.Review mate = coach.review(fools, legal(fools, "g4"), 2);
        assertEquals("blunder", mate.verdict());
        assertTrue(mate.facts().stream().anyMatch(f -> f.contains("checkmate")));
    }
}
