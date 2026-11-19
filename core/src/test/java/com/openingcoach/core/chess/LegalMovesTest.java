package com.openingcoach.core.chess;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Perft (performance test) counts every leaf of the legal-move tree to a fixed depth. The
 * expected numbers are the standard published values from the Chess Programming Wiki; matching
 * them exercises castling, en passant, promotion, pins and checks all at once.
 */
class LegalMovesTest {

    private static long perft(Board b, int depth) {
        if (depth == 0) {
            return 1;
        }
        long n = 0;
        for (Board.Move m : b.legalMoves()) {
            Board next = b.copy();
            next.applySan(m.san()); // also proves every generated SAN is accepted by the SAN parser
            n += perft(next, depth - 1);
        }
        return n;
    }

    @Test
    void perftFromStartPosition() {
        Board b = Board.startPosition();
        assertEquals(20, perft(b, 1));
        assertEquals(400, perft(b, 2));
        assertEquals(8_902, perft(b, 3));
    }

    @Test
    void perftKiwipete() {
        // "Kiwipete": castling both sides, pins, en passant and promotions within 2 plies
        Board b = Board.fromFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1");
        assertEquals(48, perft(b, 1));
        assertEquals(2_039, perft(b, 2));
    }

    @Test
    void perftPosition3EnPassantAndPins() {
        Board b = Board.fromFen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1");
        assertEquals(14, perft(b, 1));
        assertEquals(191, perft(b, 2));
        assertEquals(2_812, perft(b, 3));
    }

    @Test
    void perftPosition4Promotions() {
        Board b = Board.fromFen("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1");
        assertEquals(6, perft(b, 1));
        assertEquals(264, perft(b, 2));
        assertEquals(9_467, perft(b, 3));
    }

    @Test
    void sanUsesMinimalDisambiguation() {
        Board b = Board.fromFen("4k3/8/8/8/8/8/4K3/R6R w - - 0 1");
        Set<String> sans = b.legalMoves().stream().map(Board.Move::san).collect(Collectors.toSet());
        assertTrue(sans.contains("Rad1"));
        assertTrue(sans.contains("Rhd1"));
        assertTrue(sans.contains("Ra8+".replace("+", "")));
        Board c = Board.fromFen("4k3/8/8/R7/8/8/4K3/R7 w - - 0 1");
        Set<String> sans2 = c.legalMoves().stream().map(Board.Move::san).collect(Collectors.toSet());
        assertTrue(sans2.contains("R1a3") && sans2.contains("R5a3"), "same file -> rank is used");
    }

    @Test
    void lastMoveSquaresAreTracked() {
        Board b = Board.startPosition();
        b.applySan("e4");
        assertEquals("e2", Squares.name(b.lastMoveFrom()));
        assertEquals("e4", Squares.name(b.lastMoveTo()));
        for (String s : List.of("e5", "Nf3", "Nc6", "Bc4", "Bc5", "O-O")) {
            b.applySan(s);
        }
        assertEquals("e1", Squares.name(b.lastMoveFrom()));
        assertEquals("g1", Squares.name(b.lastMoveTo()));
    }

    @Test
    void checkmateAndStalemateHaveNoMoves() {
        Board mate = Board.startPosition();
        for (String s : List.of("f3", "e5", "g4", "Qh4")) {
            mate.applySan(s);
        }
        assertTrue(mate.legalMoves().isEmpty(), "fool's mate");
        Board stalemate = Board.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1");
        assertTrue(stalemate.legalMoves().isEmpty());
    }
}
