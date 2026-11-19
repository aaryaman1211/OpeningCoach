package com.openingcoach.core.reference;

import com.openingcoach.core.Stats;
import com.openingcoach.core.TestGames;
import com.openingcoach.core.chess.Board;
import com.openingcoach.core.pgn.ReplayedGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReferenceIndexTest {

    private static String toPgn(List<ReplayedGame> games, int elo) {
        StringBuilder sb = new StringBuilder();
        for (ReplayedGame g : games) {
            sb.append("[White \"w\"]\n[Black \"b\"]\n[Result \"").append(g.source().tag("Result")).append("\"]\n");
            sb.append("[WhiteElo \"").append(elo).append("\"]\n[BlackElo \"").append(elo).append("\"]\n\n");
            for (int i = 0; i < g.plies(); i++) {
                if (i % 2 == 0) {
                    sb.append(i / 2 + 1).append(". ");
                }
                sb.append(g.sanMoves().get(i)).append(' ');
            }
            sb.append(g.source().tag("Result")).append("\n\n");
        }
        return sb.toString();
    }

    /** Index answers must equal counts computed directly from the games, for every position. */
    @Test
    void lookupsMatchBruteForceCounts(@TempDir Path dir) throws IOException {
        List<ReplayedGame> games = TestGames.randomGames(new Random(3), 400, 10);
        ReferenceIndexBuilder.build(new StringReader(toPgn(games, 2000)), dir, 1500, 10, 0.01);
        Map<Long, Stats> expected = new HashMap<>();
        for (ReplayedGame g : games) {
            for (long k : g.keys()) {
                expected.merge(k, Stats.EMPTY.plus(g.result()), (a, b) ->
                        new Stats(a.whiteWins() + b.whiteWins(), a.draws() + b.draws(), a.blackWins() + b.blackWins()));
            }
        }
        // a game can visit the same position twice (knight out and back); the builder counts each visit,
        // the brute force above does too, so they agree.
        try (ReferenceIndex idx = ReferenceIndex.open(dir)) {
            for (Map.Entry<Long, Stats> e : expected.entrySet()) {
                assertEquals(e.getValue(), idx.position(e.getKey()).orElseThrow());
            }
            assertEquals(expected.size(), idx.positionCount());
        }
    }

    @Test
    void movesAreSortedByPopularityAndLeadToTheRightPosition(@TempDir Path dir) throws IOException {
        String pgn = """
                [Result "1-0"]
                [WhiteElo "2000"]
                [BlackElo "2000"]

                1. e4 e5 1-0

                [Result "0-1"]
                [WhiteElo "2000"]
                [BlackElo "2000"]

                1. e4 c5 0-1

                [Result "1/2-1/2"]
                [WhiteElo "2000"]
                [BlackElo "2000"]

                1. e4 c5 1/2-1/2

                [Result "1-0"]
                [WhiteElo "2000"]
                [BlackElo "2000"]

                1. d4 d5 1-0
                """;
        ReferenceIndexBuilder.build(new StringReader(pgn), dir, 1500, 10, 0.01);
        try (ReferenceIndex idx = ReferenceIndex.open(dir)) {
            Board b = Board.startPosition();
            List<ReferenceBook.RefMove> first = idx.moves(b.key());
            assertEquals("e4", first.get(0).san());
            assertEquals(3, first.get(0).stats().games());
            assertEquals("d4", first.get(1).san());
            b.applySan("e4");
            assertEquals(b.key(), first.get(0).childKey());
            List<ReferenceBook.RefMove> afterE4 = idx.moves(b.key());
            assertEquals("c5", afterE4.get(0).san());
            assertEquals(new Stats(0, 1, 1), afterE4.get(0).stats());
        }
    }

    @Test
    void unknownPositionsAreAbsentAndMostNeverTouchDisk(@TempDir Path dir) throws IOException {
        List<ReplayedGame> games = TestGames.randomGames(new Random(8), 300, 10);
        ReferenceIndexBuilder.build(new StringReader(toPgn(games, 2000)), dir, 1500, 10, 0.01);
        try (ReferenceIndex idx = ReferenceIndex.open(dir)) {
            idx.resetCounters();
            SplittableRandom rnd = new SplittableRandom(1);
            int queries = 20_000;
            for (int i = 0; i < queries; i++) {
                assertTrue(idx.position(rnd.nextLong()).isEmpty());
                assertTrue(idx.moves(rnd.nextLong()).isEmpty());
            }
            ReferenceIndex.Metrics m = idx.metrics();
            assertEquals(queries, m.bloomRejections() + m.falsePositives());
            assertTrue(m.observedFpp() < 0.03, "observed FPP " + m.observedFpp());
            assertEquals(0, m.confirmedHits());
        }
    }

    @Test
    void bloomGateNeverChangesAnswers(@TempDir Path dir) throws IOException {
        List<ReplayedGame> games = TestGames.randomGames(new Random(9), 300, 10);
        ReferenceIndexBuilder.build(new StringReader(toPgn(games, 2000)), dir, 1500, 10, 0.01);
        try (ReferenceIndex idx = ReferenceIndex.open(dir)) {
            SplittableRandom rnd = new SplittableRandom(2);
            for (int i = 0; i < 2000; i++) {
                long key = i % 2 == 0 ? games.get(i % games.size()).keys()[i % 5] : rnd.nextLong();
                idx.setBloomEnabled(true);
                var withBloom = idx.position(key);
                idx.setBloomEnabled(false);
                var without = idx.position(key);
                assertEquals(without, withBloom);
            }
        }
    }

    @Test
    void ratingFilterAndEmptyInput(@TempDir Path dir) {
        List<ReplayedGame> games = TestGames.randomGames(new Random(1), 20, 6);
        assertThrows(IllegalArgumentException.class,
                () -> ReferenceIndexBuilder.build(new StringReader(toPgn(games, 1200)), dir, 1500, 10, 0.01),
                "all games below the minimum rating -> nothing to index");
        assertThrows(IllegalArgumentException.class,
                () -> ReferenceIndexBuilder.build(new StringReader(""), dir, 0, 10, 0.01));
        assertFalse(ReferenceIndex.exists(dir.resolve("nothing-here")));
    }
}
