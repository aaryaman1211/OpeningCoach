package com.openingcoach.core.analysis;

import com.openingcoach.core.Stats;
import com.openingcoach.core.chess.Board;
import com.openingcoach.core.cover.CoverageSolver;
import com.openingcoach.core.cover.CoverageSolver.CandidateSet;
import com.openingcoach.core.pgn.GameReplayer;
import com.openingcoach.core.pgn.ReplayedGame;
import com.openingcoach.core.reference.ReferenceBook;
import com.openingcoach.core.reference.ReferenceBook.RefMove;
import com.openingcoach.core.select.RandomizedQuickSort;
import com.openingcoach.core.select.RandomizedSelect;
import com.openingcoach.core.trie.OpeningTrie;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Combines a player's {@link Repertoire} (trie of their games) with a {@link ReferenceBook}
 * (strong players' games, Bloom-gated) to answer the four questions the app exists for:
 * what do I play, where do I lose, where do I leave theory, and what should I study.
 *
 * <p>Scores are always from the player's point of view (1 = win, 0.5 = draw, 0 = loss).
 */
public final class OpeningAnalyzer {

    /** Reference statistics below this many games are treated as too thin to trust. */
    public static final int MIN_REFERENCE_GAMES = 10;

    private final Repertoire rep;
    private final ReferenceBook book;
    private final long seed;

    public OpeningAnalyzer(Repertoire rep, ReferenceBook book, long seed) {
        this.rep = rep;
        this.book = book;
        this.seed = seed;
    }

    public Repertoire repertoire() {
        return rep;
    }

    // ================================================================== summary

    public record Summary(String player, String colour, Repertoire.ImportReport importReport, int games,
                          Double score, int wins, int draws, int losses, int trieNodes, int maxPly) {
    }

    public Summary summary() {
        Stats s = rep.trie().root().stats();
        boolean w = rep.white();
        return new Summary(rep.player(), w ? "white" : "black", rep.report(), s.games(), num(s.scoreFor(w)),
                s.winsFor(w), s.draws(), s.lossesFor(w), rep.trie().nodeCount(), rep.trie().maxPly());
    }

    // ================================================================== explorer

    /** One candidate move in the explorer table. */
    public record MoveRow(String san, String from, String to, int yourGames, Double yourScore, int refGames,
                          Double refScore, Double refShare, boolean inBook) {
    }

    /** A legal move the user can play on the interactive board. */
    public record LegalMove(String san, String from, String to, String promotion) {
    }

    public record NodeView(List<String> path, String line, String fen, boolean yourTurn, int yourGames,
                           Double yourScore, int refGames, Double refScore, boolean inBook, List<MoveRow> moves,
                           String lastFrom, String lastTo, boolean check, List<LegalMove> legalMoves) {
    }

    /** The explorer view for any move path (the user may browse reference moves they never played). */
    public NodeView node(List<String> path) {
        Board board;
        try {
            board = GameReplayer.boardAfter(path);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Illegal move path: " + e.getMessage(), e);
        }
        long key = board.key();
        // normalise SAN (e.g. "Nf3+" -> "Nf3") by reading it back from a replay
        List<String> clean = new ArrayList<>();
        Board b = Board.startPosition();
        for (String san : path) {
            clean.add(b.applySan(san));
        }
        boolean w = rep.white();
        OpeningTrie.Node userNode = rep.trie().find(clean);
        Stats yours = userNode == null ? Stats.EMPTY : userNode.stats();
        Optional<Stats> ref = book.position(key);
        List<RefMove> refMoves = book.moves(key);
        int refTotal = refMoves.stream().mapToInt(m -> m.stats().games()).sum();

        List<Board.Move> legal = board.legalMoves();
        Map<String, Board.Move> legalBySan = new HashMap<>();
        List<LegalMove> legalOut = new ArrayList<>();
        for (Board.Move m : legal) {
            legalBySan.put(m.san(), m);
            legalOut.add(new LegalMove(m.san(), m.fromSquare(), m.toSquare(),
                    m.promotion() == Board.EMPTY ? null : String.valueOf("  nbrqk".charAt(m.promotion()))));
        }

        Map<String, MoveRow> rows = new LinkedHashMap<>();
        Map<String, RefMove> refBySan = new HashMap<>();
        for (RefMove m : refMoves) {
            refBySan.put(m.san(), m);
        }
        if (userNode != null) {
            for (OpeningTrie.Node c : userNode.children()) {
                RefMove rm = refBySan.get(c.san());
                boolean inBook = rm != null || book.position(c.positionKey()).isPresent();
                Board.Move lm = squaresOf(board, legalBySan, c.san());
                rows.put(c.san(), new MoveRow(c.san(), lm == null ? null : lm.fromSquare(), lm == null ? null : lm.toSquare(),
                        c.games(), num(c.stats().scoreFor(w)),
                        rm == null ? 0 : rm.stats().games(), rm == null ? null : num(rm.stats().scoreFor(w)),
                        rm == null || refTotal == 0 ? null : (double) rm.stats().games() / refTotal, inBook));
            }
        }
        for (RefMove rm : refMoves) {
            Board.Move lm = squaresOf(board, legalBySan, rm.san());
            rows.putIfAbsent(rm.san(), new MoveRow(rm.san(), lm == null ? null : lm.fromSquare(),
                    lm == null ? null : lm.toSquare(), 0, null, rm.stats().games(),
                    num(rm.stats().scoreFor(w)), refTotal == 0 ? null : (double) rm.stats().games() / refTotal, true));
        }
        List<MoveRow> list = new ArrayList<>(rows.values());
        new RandomizedQuickSort(seed).sort(list, Comparator
                .comparingInt((MoveRow r) -> -r.yourGames())
                .thenComparingInt(r -> -r.refGames())
                .thenComparing(MoveRow::san));
        boolean check = board.isAttacked(findKingSquare(board), !board.whiteToMove());
        return new NodeView(clean, MoveText.format(clean), board.toFen(), board.whiteToMove() == w,
                yours.games(), num(yours.scoreFor(w)), ref.map(Stats::games).orElse(0),
                ref.map(s -> num(s.scoreFor(w))).orElse(null), ref.isPresent(), list,
                clean.isEmpty() ? null : com.openingcoach.core.chess.Squares.name(board.lastMoveFrom()),
                clean.isEmpty() ? null : com.openingcoach.core.chess.Squares.name(board.lastMoveTo()),
                check, legalOut);
    }

    /** From/to squares of a SAN move, via the legal-move list or (for unusual SAN spellings) a trial replay. */
    private static Board.Move squaresOf(Board board, Map<String, Board.Move> legalBySan, String san) {
        Board.Move m = legalBySan.get(san);
        if (m != null) {
            return m;
        }
        try {
            Board trial = board.copy();
            String clean = trial.applySan(san);
            return new Board.Move(trial.lastMoveFrom(), trial.lastMoveTo(), Board.EMPTY, clean);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int findKingSquare(Board b) {
        int target = b.whiteToMove() ? Board.KING : -Board.KING;
        for (int s = 0; s < 64; s++) {
            if (b.pieceAt(s) == target) {
                return s;
            }
        }
        return 0;
    }

    // ================================================================== highlights (quickest wins)

    public record Highlight(String white, String black, String opponent, Integer opponentElo, String date,
                            String site, int moves, boolean mate, List<String> sanMoves, String event) {
    }

    public record HighlightsResult(List<Highlight> games, int candidates, long selectComparisons) {
    }

    /**
     * The player's k best wins of 10+ moves: checkmates first, then stronger opponents, then shorter games. Picked with
     * randomized Quickselect over all wins, then only those k are sorted.
     */
    public HighlightsResult highlights(int k, int minOpponentElo) {
        if (k <= 0) {
            throw new IllegalArgumentException("k must be positive");
        }
        boolean w = rep.white();
        record Win(com.openingcoach.core.pgn.PgnGame g, int plies, int oppElo, boolean mate) {
        }
        List<Win> wins = new ArrayList<>();
        for (ReplayedGame rg : rep.games()) {
            if (rg.result().pointsFor(w) < 1.0) {
                continue;
            }
            int opp = rg.source().elo(!w);
            if (minOpponentElo > 0 && opp < minOpponentElo) {
                continue;
            }
            int plies = rg.source().sanMoves().size();
            if (plies < 20) {
                continue; // under 10 moves is usually a resignation or flag, not a real game
            }
            List<String> raw = rg.source().sanMoves();
            wins.add(new Win(rg.source(), plies, opp, raw.get(raw.size() - 1).contains("#")));
        }
        // best = finished with checkmate first, then the strongest opponent, then the shortest
        Comparator<Win> quickest = Comparator.comparingInt((Win x) -> x.mate() ? 0 : 1)
                .thenComparingInt(x -> -x.oppElo()).thenComparingInt(Win::plies);
        RandomizedSelect select = new RandomizedSelect(seed);
        List<Win> top = select.smallestK(wins, Math.min(k, wins.size()), quickest);
        new RandomizedQuickSort(seed).sort(top, quickest);
        List<Highlight> out = new ArrayList<>();
        for (Win win : top) {
            GameReplayer.Outcome o = GameReplayer.replay(win.g(), 600);
            if (!o.ok()) {
                continue;
            }
            out.add(new Highlight(win.g().white(), win.g().black(), w ? win.g().black() : win.g().white(),
                    win.oppElo() > 0 ? win.oppElo() : null, win.g().tag("Date"), win.g().tag("Site"),
                    (win.plies() + 1) / 2, win.mate(), o.game().sanMoves(), win.g().tag("Event")));
        }
        return new HighlightsResult(out, wins.size(), select.comparisons());
    }

    // ================================================================== repertoire (top lines)

    public record TopLine(List<String> path, String line, int games, double share, Double score) {
    }

    public record TopLinesResult(List<TopLine> lines, int candidates, long selectComparisons) {
    }

    /**
     * The k most-played lines at exactly {@code depth} plies (what this player usually reaches),
     * picked with randomized Quickselect over all trie nodes at that depth.
     */
    public TopLinesResult topLines(int k, int depth) {
        if (k <= 0 || depth <= 0 || depth > rep.trie().maxPly()) {
            throw new IllegalArgumentException("k must be positive and depth in 1.." + rep.trie().maxPly());
        }
        boolean w = rep.white();
        int total = Math.max(1, rep.trie().root().games());
        List<OpeningTrie.Node> atDepth = new ArrayList<>();
        rep.trie().forEachNode(n -> {
            if (n.ply() == depth && n.games() >= 2) {
                atDepth.add(n);
            }
        });
        Comparator<OpeningTrie.Node> mostPlayed = Comparator.comparingInt((OpeningTrie.Node n) -> -n.games())
                .thenComparing(n -> MoveText.format(n.path()));
        RandomizedSelect select = new RandomizedSelect(seed);
        List<OpeningTrie.Node> top = select.smallestK(atDepth, Math.min(k, atDepth.size()), mostPlayed);
        new RandomizedQuickSort(seed).sort(top, mostPlayed);
        List<TopLine> out = new ArrayList<>();
        for (OpeningTrie.Node n : top) {
            List<String> p = n.path();
            out.add(new TopLine(p, MoveText.format(p), n.games(), round((double) n.games() / total),
                    num(n.stats().scoreFor(w))));
        }
        return new TopLinesResult(out, atDepth.size(), select.comparisons());
    }

    // ================================================================== weak lines

    public record WeakLine(List<String> path, String line, int ply, int games, Double yourScore, Double refScore,
                           double expectedScore, double pointsLost, boolean yourMoveLast) {
    }

    public record WeakLinesResult(List<WeakLine> lines, int candidates, int redundantSkipped,
                                  long selectComparisons, long sortComparisons) {
    }

    private record Candidate(OpeningTrie.Node node, double expected, Double refScore, double pointsLost) {
    }

    /**
     * Finds the k lines where the player loses the most points compared with what strong players
     * score from the same position.
     *
     * <p>pointsLost = games × (expected score − your score), where expected is the reference score
     * at that position (or your overall score when strong players rarely reached it). A line is
     * skipped as redundant when one of its own continuations already explains ≥ 75% of its loss,
     * so the list points at the most specific problem. The top k are picked with randomized
     * Quickselect in expected O(N) over N candidate nodes, then only those k are sorted.
     */
    public WeakLinesResult weakLines(int k, int minGames) {
        if (k <= 0) {
            throw new IllegalArgumentException("k must be positive");
        }
        if (minGames <= 0) {
            throw new IllegalArgumentException("minGames must be positive");
        }
        boolean w = rep.white();
        double overall = rep.trie().root().stats().scoreFor(w);
        double baseline = Double.isNaN(overall) ? 0.5 : overall;

        Map<OpeningTrie.Node, Candidate> all = new HashMap<>();
        rep.trie().forEachNode(n -> {
            if (n.ply() == 0 || n.games() < minGames) {
                return;
            }
            Optional<Stats> ref = book.position(n.positionKey());
            Double refScore = ref.filter(s -> s.games() >= MIN_REFERENCE_GAMES).map(s -> round(s.scoreFor(w))).orElse(null);
            double expected = refScore != null ? refScore : baseline;
            double lost = n.games() * (expected - n.stats().scoreFor(w));
            all.put(n, new Candidate(n, expected, refScore, lost));
        });

        List<Candidate> candidates = new ArrayList<>();
        int redundant = 0;
        for (Candidate c : all.values()) {
            if (c.pointsLost() <= 0) {
                continue;
            }
            boolean explainedByChild = false;
            for (OpeningTrie.Node child : c.node().children()) {
                Candidate cc = all.get(child);
                if (cc != null && cc.pointsLost() >= 0.75 * c.pointsLost()) {
                    explainedByChild = true;
                    break;
                }
            }
            if (explainedByChild) {
                redundant++;
            } else {
                candidates.add(c);
            }
        }

        Comparator<Candidate> worstFirst = Comparator.comparingDouble((Candidate c) -> -c.pointsLost())
                .thenComparingInt(c -> -c.node().games());
        RandomizedSelect select = new RandomizedSelect(seed);
        List<Candidate> top = select.smallestK(candidates, Math.min(k, candidates.size()), worstFirst);
        RandomizedQuickSort sorter = new RandomizedQuickSort(seed);
        sorter.sort(top, worstFirst);

        List<WeakLine> lines = new ArrayList<>();
        for (Candidate c : top) {
            OpeningTrie.Node n = c.node();
            List<String> path = n.path();
            lines.add(new WeakLine(path, MoveText.format(path), n.ply(), n.games(), num(n.stats().scoreFor(w)),
                    c.refScore(), round(c.expected()), round(c.pointsLost()), n.whiteMoved() == w));
        }
        return new WeakLinesResult(lines, candidates.size(), redundant, select.comparisons(), sorter.comparisons());
    }

    // ================================================================== out of book

    public record BookExit(List<String> path, String line, String leavingMove, boolean byYou, int games,
                           Double yourScore, List<RefAlternative> strongPlayersPlay) {
    }

    public record RefAlternative(String san, int games, Double score) {
    }

    public record OutOfBookResult(List<BookExit> exits, int gamesAnalysed, int stayedInBook, int youLeft,
                                  int opponentLeft, Double averageExitPly) {
    }

    /**
     * For every game, finds the first ply whose position strong players never reached. Each
     * position lookup goes through the Bloom filter first, so the disk index is only read for
     * positions that might be in the book.
     */
    public OutOfBookResult outOfBook(int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("k must be positive");
        }
        boolean w = rep.white();
        Map<List<String>, Stats> exitStats = new HashMap<>();
        int stayed = 0, youLeft = 0, oppLeft = 0;
        long plySum = 0;
        int exitsCount = 0;
        for (ReplayedGame g : rep.games()) {
            int exit = -1;
            for (int i = 1; i <= g.plies(); i++) {
                if (book.position(g.keys()[i]).isEmpty()) {
                    exit = i;
                    break;
                }
            }
            if (exit < 0) {
                stayed++;
                continue;
            }
            List<String> path = List.copyOf(g.sanMoves().subList(0, exit));
            exitStats.merge(path, Stats.EMPTY.plus(g.result()), OpeningAnalyzer::add);
            boolean whiteMoved = exit % 2 == 1;
            if (whiteMoved == w) {
                youLeft++;
            } else {
                oppLeft++;
            }
            plySum += exit;
            exitsCount++;
        }

        List<Map.Entry<List<String>, Stats>> entries = new ArrayList<>(exitStats.entrySet());
        Comparator<Map.Entry<List<String>, Stats>> mostFrequent = Comparator
                .comparingInt((Map.Entry<List<String>, Stats> e) -> -e.getValue().games())
                .thenComparing(e -> MoveText.format(e.getKey()));
        List<Map.Entry<List<String>, Stats>> top = new RandomizedSelect(seed)
                .smallestK(entries, Math.min(k, entries.size()), mostFrequent);
        new RandomizedQuickSort(seed).sort(top, mostFrequent);

        List<BookExit> exits = new ArrayList<>();
        for (Map.Entry<List<String>, Stats> e : top) {
            List<String> path = e.getKey();
            List<String> parent = path.subList(0, path.size() - 1);
            long parentKey = GameReplayer.boardAfter(parent).key();
            List<RefAlternative> alts = new ArrayList<>();
            for (RefMove rm : book.moves(parentKey)) {
                if (alts.size() == 3) {
                    break;
                }
                alts.add(new RefAlternative(rm.san(), rm.stats().games(), num(rm.stats().scoreFor(w))));
            }
            boolean whiteMoved = path.size() % 2 == 1;
            exits.add(new BookExit(path, MoveText.format(path), path.get(path.size() - 1), whiteMoved == w,
                    e.getValue().games(), num(e.getValue().scoreFor(w)), alts));
        }
        Double avg = exitsCount == 0 ? null : round((double) plySum / exitsCount);
        return new OutOfBookResult(exits, rep.games().size(), stayed, youLeft, oppLeft, avg);
    }

    private static Stats add(Stats a, Stats b) {
        return new Stats(a.whiteWins() + b.whiteWins(), a.draws() + b.draws(), a.blackWins() + b.blackWins());
    }

    // ================================================================== study plan

    public record StudyLine(int id, List<String> moves, String line, int plies, double minutes,
                            List<String> fixes, double pointsCovered) {
    }

    public record StudyPlan(String mode, Double budgetMinutes, List<StudyLine> lines, double totalMinutes,
                            double coverage, double pointsCovered, double pointsTotal, List<WeakLine> notCovered,
                            int candidateLines, int weakLinesConsidered, String guarantee, Double optimalValue,
                            Double greedyValue, Double measuredRatio) {
    }

    /**
     * Chooses which lines to study. Elements = the player's weak lines (weighted by points lost);
     * candidate sets = study lines, each being a weak line extended by the strong players' main
     * continuation for {@code extraPlies} plies. A study line "fixes" every weak line whose
     * position occurs along it (this includes shorter prefixes and transpositions). Cost = plies ×
     * minutesPerPly.
     *
     * @param budgetMinutes null = cover every weak line at least cost (greedy set cover);
     *                      otherwise maximise points covered within the budget (budgeted coverage)
     */
    public StudyPlan studyPlan(Double budgetMinutes, double minutesPerPly, int extraPlies, int weakCount, int minGames) {
        if (!(minutesPerPly > 0) || minutesPerPly > 60) {
            throw new IllegalArgumentException("minutesPerPly must be in (0, 60]");
        }
        if (extraPlies < 0 || extraPlies > 20) {
            throw new IllegalArgumentException("extraPlies must be in 0..20");
        }
        if (weakCount <= 0 || weakCount > 60) {
            throw new IllegalArgumentException("weakCount must be in 1..60");
        }
        if (budgetMinutes != null && (!(budgetMinutes >= 0) || budgetMinutes > 100_000)) {
            throw new IllegalArgumentException("budgetMinutes must be between 0 and 100000");
        }
        List<WeakLine> weak = weakLines(weakCount, minGames).lines();
        double[] weights = new double[weak.size()];
        Map<Long, List<Integer>> weakByKey = new HashMap<>();
        for (int i = 0; i < weak.size(); i++) {
            weights[i] = weak.get(i).pointsLost();
            long key = GameReplayer.boardAfter(weak.get(i).path()).key();
            weakByKey.computeIfAbsent(key, x -> new ArrayList<>()).add(i);
        }

        // candidate study lines
        List<CandidateSet> sets = new ArrayList<>();
        List<List<String>> lineMoves = new ArrayList<>();
        Set<List<String>> seenLines = new HashSet<>();
        for (WeakLine wl : weak) {
            Board b = Board.startPosition();
            List<String> moves = new ArrayList<>();
            Set<Integer> covers = new LinkedHashSet<>();
            addCovered(weakByKey, b.key(), covers);
            for (String san : wl.path()) {
                moves.add(b.applySan(san));
                addCovered(weakByKey, b.key(), covers);
            }
            for (int i = 0; i < extraPlies; i++) {
                List<RefMove> next = book.moves(b.key());
                if (next.isEmpty()) {
                    break;
                }
                moves.add(b.applySan(next.get(0).san()));
                addCovered(weakByKey, b.key(), covers);
            }
            if (!seenLines.add(moves)) {
                continue;
            }
            int id = sets.size();
            sets.add(new CandidateSet(id, moves.size() * minutesPerPly,
                    covers.stream().mapToInt(Integer::intValue).toArray()));
            lineMoves.add(moves);
        }

        CoverageSolver.Result greedy;
        CoverageSolver.Result exact = null;
        String mode;
        String guarantee;
        boolean smallEnough = sets.size() <= 16;
        if (budgetMinutes == null) {
            mode = "cover-all";
            greedy = CoverageSolver.greedyCover(weights, sets);
            guarantee = "Greedy set cover: total time is at most H(d) ≈ 1 + ln d times the optimum (d = most weak lines one study line fixes). Approximate, not guaranteed optimal.";
            if (smallEnough) {
                exact = CoverageSolver.exactCover(weights, sets);
            }
        } else {
            mode = "budget";
            greedy = CoverageSolver.greedyBudget(weights, sets, budgetMinutes);
            guarantee = "Budgeted coverage: better of ratio-greedy and best single line, guaranteed at least (1 − 1/e)/2 ≈ 32% of the optimum (usually far closer). Approximate, not guaranteed optimal.";
            if (smallEnough) {
                exact = CoverageSolver.exactBudget(weights, sets, budgetMinutes);
            }
        }

        List<StudyLine> lines = new ArrayList<>();
        for (int id : greedy.chosenIds()) {
            CandidateSet cs = sets.get(id);
            List<String> fixes = new ArrayList<>();
            double pts = 0;
            for (int e : cs.elements()) {
                fixes.add(weak.get(e).line());
                pts += weights[e];
            }
            List<String> mv = lineMoves.get(id);
            lines.add(new StudyLine(id, mv, MoveText.format(mv), mv.size(), round(cs.cost()), fixes, round(pts)));
        }
        List<WeakLine> notCovered = new ArrayList<>();
        for (int e : greedy.uncovered()) {
            notCovered.add(weak.get(e));
        }

        Double optimalValue = null, greedyValue = null, ratio = null;
        if (exact != null) {
            if (budgetMinutes == null) {
                optimalValue = round(exact.totalCost());
                greedyValue = round(greedy.totalCost());
                ratio = greedy.totalCost() == 0 ? 1.0 : round(greedy.totalCost() / Math.max(exact.totalCost(), 1e-9));
            } else {
                optimalValue = round(exact.coveredWeight());
                greedyValue = round(greedy.coveredWeight());
                ratio = exact.coveredWeight() == 0 ? 1.0 : round(greedy.coveredWeight() / exact.coveredWeight());
            }
        }
        return new StudyPlan(mode, budgetMinutes, lines, round(greedy.totalCost()), round(greedy.coverageFraction()),
                round(greedy.coveredWeight()), round(greedy.totalWeight()), notCovered, sets.size(), weak.size(),
                guarantee, optimalValue, greedyValue, ratio);
    }

    private static void addCovered(Map<Long, List<Integer>> weakByKey, long key, Set<Integer> covers) {
        List<Integer> hit = weakByKey.get(key);
        if (hit != null) {
            covers.addAll(hit);
        }
    }

    // ================================================================== helpers

    /** JSON has no NaN: missing scores become null. Rounded to 3 decimals for display. */
    static Double num(double v) {
        return Double.isNaN(v) ? null : round(v);
    }

    static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
