package com.openingcoach.core.analysis;

import com.openingcoach.core.chess.Board;
import com.openingcoach.core.pgn.GameReplayer;
import com.openingcoach.core.pgn.PgnGame;
import com.openingcoach.core.pgn.ReplayedGame;
import com.openingcoach.core.trie.OpeningTrie;

import java.util.ArrayList;
import java.util.List;

/** A player's games with one colour, replayed and loaded into an opening trie. */
public final class Repertoire {

    /** What happened to each imported game. */
    public record ImportReport(int gamesRead, int gamesUsed, int otherPlayerOrColour, int nonStandard,
                               int unfinished, int illegal, int noMoves, List<String> problems) {
    }

    private final String player;
    private final boolean white;
    private final OpeningTrie trie;
    private final List<ReplayedGame> games;
    private final ImportReport report;

    private Repertoire(String player, boolean white, OpeningTrie trie, List<ReplayedGame> games, ImportReport report) {
        this.player = player;
        this.white = white;
        this.trie = trie;
        this.games = games;
        this.report = report;
    }

    /**
     * Keeps games where {@code player} had the given colour, replays the first {@code maxPly}
     * plies, and inserts them into a trie. Illegal or non-standard games are counted, not hidden.
     */
    public static Repertoire build(List<PgnGame> pgnGames, String player, boolean white, int maxPly) {
        if (player == null || player.isBlank()) {
            throw new IllegalArgumentException("Player name is required to know which side is yours");
        }
        String name = player.trim();
        OpeningTrie trie = new OpeningTrie(Board.startPosition().key(), maxPly);
        List<ReplayedGame> used = new ArrayList<>();
        int other = 0, nonStandard = 0, unfinished = 0, illegal = 0, noMoves = 0;
        List<String> problems = new ArrayList<>();
        for (PgnGame g : pgnGames) {
            String side = white ? g.white() : g.black();
            if (!side.equalsIgnoreCase(name)) {
                other++;
                continue;
            }
            GameReplayer.Outcome o = GameReplayer.replay(g, maxPly);
            if (!o.ok()) {
                switch (o.skipReason()) {
                    case NON_STANDARD_START -> nonStandard++;
                    case UNFINISHED -> unfinished++;
                    case NO_MOVES -> noMoves++;
                    case ILLEGAL_MOVE -> {
                        illegal++;
                        if (problems.size() < 5) {
                            String site = g.tag("Site");
                            problems.add((site.isEmpty() ? "game" : site) + " - " + o.detail());
                        }
                    }
                }
                continue;
            }
            trie.insert(o.game());
            used.add(o.game());
        }
        ImportReport report = new ImportReport(pgnGames.size(), used.size(), other, nonStandard, unfinished,
                illegal, noMoves, List.copyOf(problems));
        return new Repertoire(name, white, trie, List.copyOf(used), report);
    }

    public String player() {
        return player;
    }

    public boolean white() {
        return white;
    }

    public OpeningTrie trie() {
        return trie;
    }

    public List<ReplayedGame> games() {
        return games;
    }

    public ImportReport report() {
        return report;
    }
}
