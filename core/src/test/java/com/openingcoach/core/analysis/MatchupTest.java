package com.openingcoach.core.analysis;

import com.openingcoach.core.TestGames;
import com.openingcoach.core.pgn.GameResult;
import com.openingcoach.core.pgn.PgnGame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchupTest {

    private static List<PgnGame> games(String white, String black, int n, GameResult r, String... sans) {
        List<PgnGame> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(TestGames.pgn(white, black, r, List.of(sans)));
        }
        return out;
    }

    /**
     * You (White) crush the Caro-Kann Advance; the opponent (Black) plays the Caro-Kann a lot and
     * scores badly in the Advance, but well against your Italian.
     */
    private static Matchup.MatchupResult scenario(int minGames) {
        List<PgnGame> mine = new ArrayList<>();
        mine.addAll(games("me", "x", 8, GameResult.WHITE_WIN, "e4", "c6", "d4", "d5", "e5"));
        mine.addAll(games("me", "x", 6, GameResult.BLACK_WIN, "e4", "e5", "Nf3", "Nc6", "Bc4"));
        List<PgnGame> theirs = new ArrayList<>();
        theirs.addAll(games("y", "opp", 7, GameResult.WHITE_WIN, "e4", "c6", "d4", "d5", "e5"));
        theirs.addAll(games("y", "opp", 9, GameResult.DRAW, "e4", "c6", "d4", "d5", "Nc3"));
        theirs.addAll(games("y", "opp", 5, GameResult.BLACK_WIN, "e4", "e5", "Nf3", "Nc6", "Bc4"));
        theirs.addAll(games("y", "opp", 3, GameResult.BLACK_WIN, "d4", "d5"));
        Repertoire you = Repertoire.build(mine, "me", true, 20);
        Repertoire them = Repertoire.build(theirs, "opp", false, 20);
        return Matchup.compare(you, them, minGames, 5, 1);
    }

    @Test
    void bestWeaponIsTheAdvanceVariation() {
        Matchup.MatchupResult r = scenario(2);
        assertFalse(r.weapons().isEmpty());
        Matchup.MatchupLine top = r.weapons().get(0);
        assertTrue(top.line().startsWith("1.e4 c6"), top.line());
        assertEquals(1.0, top.yourScore());
        for (Matchup.MatchupLine m : r.weapons()) {
            assertTrue(m.edge() > 0);
        }
    }

    @Test
    void dangersContainTheItalian() {
        Matchup.MatchupResult r = scenario(2);
        assertTrue(r.dangers().stream().anyMatch(m -> m.line().startsWith("1.e4 e5")));
        for (Matchup.MatchupLine m : r.dangers()) {
            assertTrue(m.edge() < 0);
        }
    }

    @Test
    void onlySharedLinesAreVisited() {
        Matchup.MatchupResult r = scenario(1);
        // shared: e4, e4 c6, ... d4 d5 e5 (5) and e4 e5 ... Bc4 (4 more) -> 9 lines, plus root
        assertEquals(9, r.sharedLines());
        assertEquals(10, r.visitedNodes());
    }

    @Test
    void predictedGameFollowsEachSidesFavourite() {
        Matchup.MatchupResult r = scenario(2);
        assertEquals("e4", r.predicted().get(0).san());
        assertTrue(r.predicted().get(0).yourMove());
        assertEquals("c6", r.predicted().get(1).san(), "opponent's most common reply to 1.e4");
        assertFalse(r.predicted().get(1).yourMove());
    }

    @Test
    void sameColoursAndBadParametersAreRejected() {
        Repertoire a = Repertoire.build(List.of(), "me", true, 20);
        Repertoire b = Repertoire.build(List.of(), "opp", true, 20);
        assertThrows(IllegalArgumentException.class, () -> Matchup.compare(a, b, 1, 5, 1));
        Repertoire c = Repertoire.build(List.of(), "opp", false, 20);
        assertThrows(IllegalArgumentException.class, () -> Matchup.compare(a, c, 0, 5, 1));
        Matchup.MatchupResult empty = Matchup.compare(a, c, 1, 5, 1);
        assertTrue(empty.weapons().isEmpty() && empty.predicted().isEmpty());
    }
}
