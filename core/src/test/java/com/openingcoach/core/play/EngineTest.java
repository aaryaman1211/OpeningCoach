package com.openingcoach.core.play;

import com.openingcoach.core.chess.Board;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineTest {

    @Test
    void findsMateInOne() {
        // White: Qh5 with Bc4 against f7 (Scholar's mate pattern); Qxf7 is mate
        Board b = Board.fromFen("r1bqkbnr/pppp1ppp/2n5/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4");
        Engine.Scored s = new Engine().best(b, 3, 5000);
        assertEquals("Qxf7", s.move().san());
        assertTrue(s.score() > Engine.MATE - 100);
    }

    @Test
    void takesAHangingQueen() {
        Board b = Board.fromFen("rnb1kbnr/pppp1ppp/8/4p3/4P2q/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3");
        assertEquals("Nxh4", new Engine().best(b, 2, 5000).move().san());
    }

    @Test
    void doesNotGrabAPoisonedPawnThatLosesTheQueen() {
        // d5 is defended by the e6 pawn and b7 by the c8 bishop: either pawn grab loses the queen
        Board b = Board.fromFen("rnbqkb1r/ppp2ppp/4pn2/3p4/3P4/1Q6/PPP1PPPP/RNB1KBNR w KQkq - 0 4");
        Engine.Scored s = new Engine().best(b, 3, 5000);
        assertTrue(!s.move().san().equals("Qxd5"), "the engine must not give the queen for a pawn");
    }

    @Test
    void rankScoresEveryMoveBestFirst() {
        Board b = Board.startPosition();
        List<Engine.Scored> ranked = new Engine().rank(b, 2);
        assertEquals(20, ranked.size());
        for (int i = 1; i < ranked.size(); i++) {
            assertTrue(ranked.get(i - 1).score() >= ranked.get(i).score());
        }
    }

    @Test
    void boundaryNoLegalMoves() {
        Board mated = Board.fromFen("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3"); // fool's mate
        assertTrue(mated.inCheck());
        assertNull(new Engine().best(mated, 3, 1000));
        Board stalemate = Board.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1");
        assertNull(new Engine().best(stalemate, 3, 1000));
    }

    @Test
    void respectsTheTimeBudget() {
        Board b = Board.fromFen("r1bq1rk1/pp2bppp/2n1pn2/2pp4/2PP4/2N1PN2/PP2BPPP/R2QKB1R w KQ - 0 8");
        long t0 = System.currentTimeMillis();
        assertNotNull(new Engine().best(b, 8, 400));
        assertTrue(System.currentTimeMillis() - t0 < 2000);
    }
}
