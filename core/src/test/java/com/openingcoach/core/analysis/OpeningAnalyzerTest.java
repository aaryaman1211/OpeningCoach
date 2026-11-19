package com.openingcoach.core.analysis;

import com.openingcoach.core.Stats;
import com.openingcoach.core.TestGames;
import com.openingcoach.core.chess.Board;
import com.openingcoach.core.pgn.GameResult;
import com.openingcoach.core.pgn.PgnGame;
import com.openingcoach.core.reference.ReferenceBook;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpeningAnalyzerTest {

    /** In-memory reference book built from a few lines, for tests. */
    static final class FakeBook implements ReferenceBook {
        final Map<Long, Stats> positions = new HashMap<>();
        final Map<Long, List<RefMove>> moves = new HashMap<>();

        FakeBook line(int games, double whiteScore, String... sans) {
            int ww = (int) Math.round(games * whiteScore), bw = games - ww;
            Stats s = new Stats(ww, 0, bw);
            Board b = Board.startPosition();
            positions.merge(b.key(), s, OpeningAnalyzerTest::add);
            for (String san : sans) {
                long parent = b.key();
                String clean = b.applySan(san);
                positions.merge(b.key(), s, OpeningAnalyzerTest::add);
                List<RefMove> list = moves.computeIfAbsent(parent, k -> new ArrayList<>());
                long child = b.key();
                boolean found = false;
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).san().equals(clean)) {
                        list.set(i, new RefMove(clean, child, add(list.get(i).stats(), s)));
                        found = true;
                    }
                }
                if (!found) {
                    list.add(new RefMove(clean, child, s));
                }
                list.sort((x, y) -> y.stats().games() - x.stats().games());
            }
            return this;
        }

        @Override
        public Optional<Stats> position(long key) {
            return Optional.ofNullable(positions.get(key));
        }

        @Override
        public List<RefMove> moves(long key) {
            return moves.getOrDefault(key, List.of());
        }
    }

    static Stats add(Stats a, Stats b) {
        return new Stats(a.whiteWins() + b.whiteWins(), a.draws() + b.draws(), a.blackWins() + b.blackWins());
    }

    private static List<PgnGame> games(String player, int n, GameResult r, String... sans) {
        List<PgnGame> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(TestGames.pgn(player, "opp" + i, r, List.of(sans)));
        }
        return out;
    }

    /** Player as White: fine in the Italian, but loses every Scandinavian game after 3.Bc4. */
    private static OpeningAnalyzer scenario() {
        List<PgnGame> g = new ArrayList<>();
        g.addAll(games("riya", 10, GameResult.WHITE_WIN, "e4", "e5", "Nf3", "Nc6", "Bc4"));
        g.addAll(games("riya", 8, GameResult.BLACK_WIN, "e4", "d5", "exd5", "Qxd5", "Nc3", "Qa5", "Bc4"));
        g.addAll(games("someone", 5, GameResult.WHITE_WIN, "d4", "d5")); // not riya: ignored
        Repertoire rep = Repertoire.build(g, "Riya", true, 20);
        FakeBook book = new FakeBook()
                .line(100, 0.55, "e4", "e5", "Nf3", "Nc6", "Bc4")
                .line(60, 0.6, "e4", "d5", "exd5", "Qxd5", "Nc3", "Qa5", "d4")
                .line(20, 0.5, "e4", "d5", "exd5", "Qxd5", "Nc3", "Qa5", "Nf3");
        return new OpeningAnalyzer(rep, book, 7);
    }

    @Test
    void importKeepsOnlyThePlayersGamesWithTheChosenColour() {
        OpeningAnalyzer a = scenario();
        Repertoire.ImportReport r = a.repertoire().report();
        assertEquals(23, r.gamesRead());
        assertEquals(18, r.gamesUsed());
        assertEquals(5, r.otherPlayerOrColour());
        assertEquals(18, a.summary().games());
        assertEquals(10.0 / 18, a.summary().score(), 0.001);
    }

    @Test
    void weakestLineIsTheScandinavianBishopMove() {
        OpeningAnalyzer.WeakLinesResult res = scenario().weakLines(5, 3);
        assertFalse(res.lines().isEmpty());
        OpeningAnalyzer.WeakLine worst = res.lines().get(0);
        assertEquals(List.of("e4", "d5", "exd5", "Qxd5", "Nc3", "Qa5", "Bc4"), worst.path());
        assertEquals(0.0, worst.yourScore());
        assertTrue(worst.yourMoveLast());
        // shorter Scandinavian prefixes are explained by this deeper line, so they are not repeated
        assertTrue(res.redundantSkipped() > 0);
        for (OpeningAnalyzer.WeakLine l : res.lines()) {
            assertTrue(l.pointsLost() > 0);
        }
    }

    @Test
    void outOfBookFindsTheMoveStrongPlayersNeverPlayed() {
        OpeningAnalyzer.OutOfBookResult res = scenario().outOfBook(5);
        assertEquals(10, res.stayedInBook());
        assertEquals(8, res.youLeft());
        OpeningAnalyzer.BookExit e = res.exits().get(0);
        assertEquals("Bc4", e.leavingMove());
        assertTrue(e.byYou());
        assertEquals("d4", e.strongPlayersPlay().get(0).san(), "strong players' main move is suggested");
    }

    @Test
    void explorerMergesYourMovesWithReferenceMoves() {
        OpeningAnalyzer.NodeView v = scenario().node(List.of("e4", "d5", "exd5", "Qxd5", "Nc3", "Qa5"));
        assertTrue(v.yourTurn());
        assertEquals(8, v.yourGames());
        List<String> sans = v.moves().stream().map(OpeningAnalyzer.MoveRow::san).toList();
        assertTrue(sans.containsAll(List.of("Bc4", "d4", "Nf3")));
        OpeningAnalyzer.MoveRow bc4 = v.moves().stream().filter(m -> m.san().equals("Bc4")).findFirst().orElseThrow();
        assertFalse(bc4.inBook());
        assertEquals(0, bc4.refGames());
        assertNotNull(v.fen());
        assertThrows(IllegalArgumentException.class, () -> scenario().node(List.of("e4", "e4")));
    }

    @Test
    void studyPlanCoversWeakLinesAndRespectsBudget() {
        OpeningAnalyzer a = scenario();
        OpeningAnalyzer.StudyPlan all = a.studyPlan(null, 2, 4, 10, 3);
        assertEquals("cover-all", all.mode());
        assertEquals(1.0, all.coverage(), 1e-9);
        assertTrue(all.lines().get(0).moves().contains("d4") || all.lines().get(0).moves().contains("Bc4"));
        assertNotNull(all.measuredRatio(), "small instance -> compared with brute force");
        assertTrue(all.measuredRatio() >= 1.0 - 1e-9);

        OpeningAnalyzer.StudyPlan tiny = a.studyPlan(1.0, 2, 4, 10, 3);
        assertTrue(tiny.totalMinutes() <= 1.0);
        assertTrue(tiny.lines().isEmpty(), "no line fits in one minute");
        assertEquals(0.0, tiny.coverage(), 1e-9);
    }

    @Test
    void invalidParametersAreRejected() {
        OpeningAnalyzer a = scenario();
        assertThrows(IllegalArgumentException.class, () -> a.weakLines(0, 3));
        assertThrows(IllegalArgumentException.class, () -> a.studyPlan(-5.0, 2, 4, 10, 3));
        assertThrows(IllegalArgumentException.class, () -> a.studyPlan(60.0, 0, 4, 10, 3));
        assertThrows(IllegalArgumentException.class,
                () -> Repertoire.build(List.of(), " ", true, 20));
    }

    @Test
    void playerWithNoGamesGivesEmptyResultsNotErrors() {
        Repertoire rep = Repertoire.build(List.of(), "nobody", false, 20);
        OpeningAnalyzer a = new OpeningAnalyzer(rep, new FakeBook(), 1);
        assertEquals(0, a.summary().games());
        assertTrue(a.weakLines(5, 1).lines().isEmpty());
        assertTrue(a.outOfBook(5).exits().isEmpty());
        assertTrue(a.studyPlan(30.0, 2, 4, 10, 1).lines().isEmpty());
    }
}
