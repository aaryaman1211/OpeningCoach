package com.openingcoach.core.chess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoardTest {

    private static Board play(String... sans) {
        Board b = Board.startPosition();
        for (String s : sans) {
            b.applySan(s);
        }
        return b;
    }

    @Test
    void startPositionRoundTripsThroughFen() {
        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", Board.startPosition().toFen());
    }

    @Test
    void transpositionsGiveTheSameKey() {
        Board a = play("Nf3", "d5", "d4");
        Board b = play("d4", "d5", "Nf3");
        assertEquals(a.key(), b.key(), "1.Nf3 d5 2.d4 and 1.d4 d5 2.Nf3 reach the same position");
    }

    @Test
    void sideToMoveChangesTheKey() {
        // same placement, different side to move: knights out and back
        Board a = Board.startPosition();
        Board b = play("Nf3", "Nf6", "Ng1", "Ng8");
        assertEquals(a.key(), b.key(), "returning to the start with White to move is the same position");
        Board c = play("Nf3", "Nf6", "Ng1");
        assertNotEquals(a.key(), c.key());
    }

    @Test
    void castlingBothSides() {
        Board b = play("e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5", "O-O", "d6", "d3", "Bg4", "Nc3", "Qd7", "Be3", "O-O-O");
        assertEquals("2kr2nr/pppq1ppp/2np4/2b1p3/2B1P1b1/2NPBN2/PPP2PPP/R2Q1RK1 w - - 5 8", b.toFen());
    }

    @Test
    void enPassantCapture() {
        Board b = play("e4", "a6", "e5", "d5", "exd6");
        assertEquals(Board.EMPTY, b.pieceAt(Squares.parse("d5")), "captured pawn is removed");
        assertEquals(Board.PAWN, b.pieceAt(Squares.parse("d6")));
    }

    @Test
    void promotionWithAndWithoutEqualsSign() {
        Board b = Board.fromFen("8/P6k/8/8/8/8/8/K7 w - - 0 1");
        b.applySan("a8=Q+");
        assertEquals(Board.QUEEN, b.pieceAt(Squares.parse("a8")));
        Board c = Board.fromFen("8/P6k/8/8/8/8/8/K7 w - - 0 1");
        c.applySan("a8N");
        assertEquals(Board.KNIGHT, c.pieceAt(Squares.parse("a8")));
    }

    @Test
    void pinnedPieceIsNotAmbiguous() {
        // Knights on c3 and g1 can both reach e2, but the c3 knight is pinned by the b4 bishop.
        Board b = Board.fromFen("4k3/8/8/8/1b6/2N5/8/4K1N1 w - - 0 1");
        b.applySan("Ne2");
        assertEquals(Board.KNIGHT, b.pieceAt(Squares.parse("e2")));
        assertEquals(Board.KNIGHT, b.pieceAt(Squares.parse("c3")), "pinned knight stayed");
    }

    @Test
    void explicitDisambiguation() {
        Board b = Board.fromFen("4k3/8/8/8/8/8/8/R3K2R w - - 0 1");
        b.applySan("Rad1");
        assertEquals(Board.ROOK, b.pieceAt(Squares.parse("d1")));
        assertEquals(Board.ROOK, b.pieceAt(Squares.parse("h1")));
    }

    @Test
    void ambiguousMoveIsRejected() {
        Board b = Board.fromFen("4k3/8/8/8/8/8/4K3/R6R w - - 0 1");
        assertThrows(IllegalMoveException.class, () -> b.applySan("Rd1"));
    }

    @Test
    void illegalMovesAreRejected() {
        assertThrows(IllegalMoveException.class, () -> play("e5"));          // pawn cannot jump 3
        assertThrows(IllegalMoveException.class, () -> play("Nf3", "Nf3"));  // black has no knight reaching f3... on f6 side
        assertThrows(IllegalMoveException.class, () -> play("O-O"));         // pieces in the way
        assertThrows(IllegalMoveException.class, () -> play("Zz9"));         // garbage
        assertThrows(IllegalMoveException.class, () -> play(""));            // empty
    }

    @Test
    void cannotCastleThroughCheck() {
        Board b = Board.fromFen("4k3/8/8/8/8/8/5r2/4K2R w K - 0 1");
        assertThrows(IllegalMoveException.class, () -> b.applySan("O-O"));
    }

    @Test
    void checkAndAnnotationSymbolsAreIgnored() {
        Board b = play("e4!", "e5?!", "Qh5", "Nc6", "Bc4", "Nf6??", "Qxf7#");
        assertEquals(Board.QUEEN, b.pieceAt(Squares.parse("f7")));
    }
}
