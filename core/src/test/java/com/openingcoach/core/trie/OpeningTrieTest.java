package com.openingcoach.core.trie;

import com.openingcoach.core.Stats;
import com.openingcoach.core.TestGames;
import com.openingcoach.core.chess.Board;
import com.openingcoach.core.pgn.GameResult;
import com.openingcoach.core.pgn.ReplayedGame;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpeningTrieTest {

    private static OpeningTrie trieOf(List<ReplayedGame> games, int maxPly) {
        OpeningTrie t = new OpeningTrie(Board.startPosition().key(), maxPly);
        games.forEach(t::insert);
        return t;
    }

    @Test
    void countsGamesAndResultsAlongEachLine() {
        List<ReplayedGame> games = List.of(
                TestGames.replay(GameResult.WHITE_WIN, "e4", "c5", "Nf3"),
                TestGames.replay(GameResult.BLACK_WIN, "e4", "c5", "Nc3"),
                TestGames.replay(GameResult.DRAW, "e4", "e5"),
                TestGames.replay(GameResult.WHITE_WIN, "d4", "d5"));
        OpeningTrie t = trieOf(games, 20);
        assertEquals(new Stats(2, 1, 1), t.root().stats());
        assertEquals(new Stats(1, 1, 1), t.find(List.of("e4")).stats());
        assertEquals(new Stats(1, 0, 1), t.find(List.of("e4", "c5")).stats());
        assertEquals(0.5, t.find(List.of("e4", "c5")).stats().scoreFor(true));
        assertEquals(4, t.gameCount());
    }

    @Test
    void missingLineReturnsNull() {
        OpeningTrie t = trieOf(List.of(TestGames.replay(GameResult.DRAW, "e4", "e5")), 20);
        assertNull(t.find(List.of("d4")));
        assertNull(t.find(List.of("e4", "c5")));
    }

    @Test
    void emptyPathIsTheRoot() {
        OpeningTrie t = trieOf(List.of(), 20);
        assertEquals(t.root(), t.find(List.of()));
        assertEquals(0, t.root().games());
        assertEquals(1, t.nodeCount());
    }

    @Test
    void insertionStopsAtMaxPly() {
        OpeningTrie t = trieOf(List.of(TestGames.replay(GameResult.DRAW, "e4", "e5", "Nf3", "Nc6", "Bb5")), 2);
        assertEquals(3, t.nodeCount(), "root + 2 plies");
        assertNull(t.find(List.of("e4", "e5", "Nf3")));
    }

    @Test
    void rejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> new OpeningTrie(0L, 0));
        OpeningTrie t = new OpeningTrie(0L, 10);
        assertThrows(IllegalArgumentException.class,
                () -> t.insert(List.of("e4"), new long[]{0L, 1L}, GameResult.UNFINISHED));
        assertThrows(IllegalArgumentException.class,
                () -> t.insert(List.of("e4", "e5"), new long[]{0L}, GameResult.DRAW));
    }

    @Test
    void pathAndPositionKeyMatchAReplay() {
        OpeningTrie t = trieOf(List.of(TestGames.replay(GameResult.DRAW, "d4", "Nf6", "c4")), 20);
        OpeningTrie.Node n = t.find(List.of("d4", "Nf6", "c4"));
        assertEquals(List.of("d4", "Nf6", "c4"), n.path());
        Board b = Board.startPosition();
        b.applySan("d4");
        b.applySan("Nf6");
        b.applySan("c4");
        assertEquals(b.key(), n.positionKey());
        assertEquals(3, n.ply());
    }

    /** Stress: trie answers must equal a brute-force scan for many random games and prefixes. */
    @Test
    void matchesLinearScanOnRandomGames() {
        Random rnd = new Random(42);
        List<ReplayedGame> games = TestGames.randomGames(rnd, 3000, 12);
        OpeningTrie t = trieOf(games, 12);
        for (int q = 0; q < 500; q++) {
            ReplayedGame g = games.get(rnd.nextInt(games.size()));
            int len = rnd.nextInt(g.plies() + 1);
            List<String> path = g.sanMoves().subList(0, len);
            assertEquals(OpeningTrie.linearScan(games, path), t.find(path).stats(), "prefix " + path);
        }
    }
}
