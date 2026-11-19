package com.openingcoach.core.analysis;

import com.openingcoach.core.select.RandomizedQuickSort;
import com.openingcoach.core.select.RandomizedSelect;
import com.openingcoach.core.trie.OpeningTrie;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * Opponent preparation: compares your repertoire with an opponent's when you play opposite
 * colours (you as White vs their Black games, or the reverse).
 *
 * <p>Both repertoires are tries over the same move alphabet, so the lines you have both played
 * are exactly the nodes reachable in both tries. A simultaneous depth-first walk visits only
 * those shared nodes: O(S) for S shared lines, never the whole of either trie.
 *
 * <p>For each shared line: edge = (your score − their score) / 2, in [−0.5, 0.5], where both
 * scores are from each player's own side. Positive = you do well and they do badly there.
 * Lines are ranked by edge × √(min games), so a big edge from 2 games does not outrank a
 * solid edge from 20. The top weapons and dangers are picked with randomized Quickselect.
 */
public final class Matchup {

    public record MatchupLine(List<String> path, String line, int yourGames, Double yourScore, int theirGames,
                              Double theirScore, double theyReachShare, double edge, double weight) {
    }

    /** One ply of the predicted game: who moves and how often they chose this move. */
    public record PredictedMove(String san, boolean yourMove, int games, double share) {
    }

    public record MatchupResult(List<MatchupLine> weapons, List<MatchupLine> dangers, List<PredictedMove> predicted,
                                String predictedLine, int sharedLines, int visitedNodes, long selectComparisons) {
    }

    private Matchup() {
    }

    public static MatchupResult compare(Repertoire you, Repertoire them, int minGames, int k, long seed) {
        if (you.white() == them.white()) {
            throw new IllegalArgumentException("Pick opposite colours: analyse your "
                    + (you.white() ? "White" : "Black") + " games against the opponent's "
                    + (you.white() ? "Black" : "White") + " games");
        }
        if (minGames <= 0 || k <= 0) {
            throw new IllegalArgumentException("minGames and k must be positive");
        }
        boolean yw = you.white(), tw = them.white();
        int theirTotal = Math.max(1, them.trie().root().games());

        List<MatchupLine> shared = new ArrayList<>();
        int visited = 0;
        Deque<OpeningTrie.Node[]> stack = new ArrayDeque<>();
        stack.push(new OpeningTrie.Node[]{you.trie().root(), them.trie().root()});
        while (!stack.isEmpty()) {
            OpeningTrie.Node[] pair = stack.pop();
            visited++;
            OpeningTrie.Node a = pair[0], b = pair[1];
            if (a.ply() > 0 && a.games() >= minGames && b.games() >= minGames) {
                double ys = a.stats().scoreFor(yw), ts = b.stats().scoreFor(tw);
                double edge = (ys - ts) / 2;
                double weight = edge * Math.sqrt(Math.min(a.games(), b.games()));
                List<String> p = a.path();
                shared.add(new MatchupLine(p, MoveText.format(p), a.games(), OpeningAnalyzer.num(ys), b.games(),
                        OpeningAnalyzer.num(ts), OpeningAnalyzer.round((double) b.games() / theirTotal),
                        OpeningAnalyzer.round(edge), OpeningAnalyzer.round(weight)));
            }
            for (OpeningTrie.Node ca : a.children()) {
                OpeningTrie.Node cb = b.child(ca.san());
                if (cb != null) {
                    stack.push(new OpeningTrie.Node[]{ca, cb});
                }
            }
        }

        RandomizedSelect select = new RandomizedSelect(seed);
        RandomizedQuickSort sorter = new RandomizedQuickSort(seed);
        Comparator<MatchupLine> best = Comparator.comparingDouble((MatchupLine m) -> -m.weight()).thenComparing(MatchupLine::line);
        Comparator<MatchupLine> worst = Comparator.comparingDouble(MatchupLine::weight).thenComparing(MatchupLine::line);
        List<MatchupLine> positive = shared.stream().filter(m -> m.weight() > 0).toList();
        List<MatchupLine> negative = shared.stream().filter(m -> m.weight() < 0).toList();
        List<MatchupLine> weapons = new ArrayList<>(select.smallestK(positive, Math.min(k, positive.size()), best));
        List<MatchupLine> dangers = new ArrayList<>(select.smallestK(negative, Math.min(k, negative.size()), worst));
        sorter.sort(weapons, best);
        sorter.sort(dangers, worst);

        // predicted game: each side plays its own most frequent move; stop when the mover has no data
        List<PredictedMove> predicted = new ArrayList<>();
        List<String> line = new ArrayList<>();
        OpeningTrie.Node ya = you.trie().root(), tb = them.trie().root();
        for (int ply = 0; ply < Math.min(you.trie().maxPly(), them.trie().maxPly()); ply++) {
            boolean whiteToMove = ply % 2 == 0;
            boolean yourMove = whiteToMove == yw;
            OpeningTrie.Node mover = yourMove ? ya : tb;
            if (mover == null || mover.children().isEmpty()) {
                break;
            }
            OpeningTrie.Node pick = null;
            for (OpeningTrie.Node c : mover.children()) {
                if (pick == null || c.games() > pick.games() || (c.games() == pick.games() && c.san().compareTo(pick.san()) < 0)) {
                    pick = c;
                }
            }
            predicted.add(new PredictedMove(pick.san(), yourMove, pick.games(),
                    OpeningAnalyzer.round((double) pick.games() / Math.max(1, mover.games()))));
            line.add(pick.san());
            ya = ya == null ? null : ya.child(pick.san());
            tb = tb == null ? null : tb.child(pick.san());
        }
        return new MatchupResult(weapons, dangers, predicted, MoveText.format(line), shared.size(), visited,
                select.comparisons());
    }
}
