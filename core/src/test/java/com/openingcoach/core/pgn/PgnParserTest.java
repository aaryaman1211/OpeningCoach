package com.openingcoach.core.pgn;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PgnParserTest {

    private static final String TWO_GAMES = """
            [Event "Rated Blitz game"]
            [White "alice"]
            [Black "bob"]
            [Result "1-0"]
            [WhiteElo "1500"]

            1. e4 {[%clk 0:03:00]} 1... e5 2. Nf3 (2. f4 exf4 (2... d5)) 2... Nc6 $1 3. Bb5 1-0

            [Event "Rated Blitz game"]
            [White "bob"]
            [Black "alice"]
            [Result "1/2-1/2"]

            1. d4 d5 ; a line comment
            2. c4 1/2-1/2
            """;

    @Test
    void readsTagsMovesAndSkipsCommentsAndVariations() {
        List<PgnGame> games = PgnParser.parseAll(TWO_GAMES);
        assertEquals(2, games.size());
        assertEquals(List.of("e4", "e5", "Nf3", "Nc6", "Bb5"), games.get(0).sanMoves());
        assertEquals("alice", games.get(0).white());
        assertEquals(GameResult.WHITE_WIN, games.get(0).result());
        assertEquals(1500, games.get(0).elo(true));
        assertEquals(-1, games.get(0).elo(false), "missing Elo becomes -1");
        assertEquals(List.of("d4", "d5", "c4"), games.get(1).sanMoves());
        assertEquals(GameResult.DRAW, games.get(1).result());
    }

    @Test
    void emptyInputGivesNoGames() {
        assertTrue(PgnParser.parseAll("").isEmpty());
        assertTrue(PgnParser.parseAll(null).isEmpty());
        assertTrue(PgnParser.parseAll("\n\n   \n").isEmpty());
    }

    @Test
    void movetextWithoutHeadersIsStillAGame() {
        List<PgnGame> games = PgnParser.parseAll("1.e4 e5 2.Nf3 *");
        assertEquals(1, games.size());
        assertEquals(3, games.get(0).sanMoves().size());
        assertEquals(GameResult.UNFINISHED, games.get(0).result());
    }

    @Test
    void chess960AndCustomStartsAreFlagged() {
        String pgn = """
                [Variant "Chess960"]
                [Result "1-0"]
                [FEN "bqnb1rkr/pp3ppp/3ppn2/2p5/5P2/P2P4/NPP1P1PP/BQ1BNRKR w HFhf - 2 9"]
                [SetUp "1"]

                9. g3 1-0
                """;
        PgnGame g = PgnParser.parseAll(pgn).get(0);
        assertFalse(g.isStandardStart());
        assertEquals(GameReplayer.SkipReason.NON_STANDARD_START, GameReplayer.replay(g, 10).skipReason());
    }

    @Test
    void illegalMoveIsReportedNotHidden() {
        PgnGame g = PgnParser.parseAll("[Result \"1-0\"]\n\n1. e4 e5 2. Ke3 1-0").get(0);
        GameReplayer.Outcome o = GameReplayer.replay(g, 20);
        assertFalse(o.ok());
        assertEquals(GameReplayer.SkipReason.ILLEGAL_MOVE, o.skipReason());
        assertTrue(o.detail().startsWith("ply 3"));
    }

    @Test
    void replayStopsAtMaxPlyAndRecordsOneKeyPerPly() {
        PgnGame g = PgnParser.parseAll(TWO_GAMES).get(0);
        ReplayedGame r = GameReplayer.replay(g, 3).game();
        assertEquals(3, r.plies());
        assertEquals(4, r.keys().length, "start key plus one key per ply");
    }
}
