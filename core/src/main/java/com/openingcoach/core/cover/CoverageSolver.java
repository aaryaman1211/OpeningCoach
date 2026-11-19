package com.openingcoach.core.cover;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Set cover and budgeted maximum coverage (Unit 5: approximation algorithms; Unit 6: NP-hardness).
 *
 * <p>Instance: elements 0..n-1 with weights (here: weak opening positions, weighted by points
 * lost), and candidate sets with costs (here: study lines, costed in minutes).
 *
 * <ul>
 *   <li>{@link #greedyCover}: cover every coverable element at minimum cost. NP-hard; greedy
 *       picks the set with the lowest cost per newly covered element and is within a factor
 *       H(d) ≤ 1 + ln d of optimal, where d is the largest set size.</li>
 *   <li>{@link #greedyBudget}: maximise covered weight with total cost ≤ budget. NP-hard;
 *       ratio-greedy alone has no constant guarantee with unequal costs, so we return the better
 *       of ratio-greedy and the best single affordable set, which guarantees at least
 *       (1 - 1/e)/2 ≈ 0.316 of optimal (Khuller, Moss &amp; Naor 1999). With equal costs plain
 *       greedy reaches 1 - 1/e ≈ 0.632.</li>
 *   <li>{@link #exactCover} / {@link #exactBudget}: brute force over all 2^s subsets, feasible
 *       only for s ≤ 22; used to measure the greedy's real ratio on small instances.</li>
 * </ul>
 * Results are labelled approximate; they are never claimed to be optimal.
 */
public final class CoverageSolver {

    public static final int EXACT_LIMIT = 22;

    private CoverageSolver() {
    }

    /** A candidate set: an id chosen by the caller, a positive cost, and the elements it covers. */
    public record CandidateSet(int id, double cost, int[] elements) {
        public CandidateSet {
            if (!(cost > 0) || Double.isInfinite(cost)) {
                throw new IllegalArgumentException("Set cost must be positive and finite");
            }
            elements = elements.clone();
        }
    }

    /**
     * @param chosenIds    ids of the chosen sets, in the order picked
     * @param totalCost    sum of their costs
     * @param coveredWeight weight of covered elements
     * @param totalWeight  weight of all elements
     * @param uncovered    elements left uncovered (including ones no set can cover)
     * @param uncoverable  elements that appear in no candidate set at all
     */
    public record Result(List<Integer> chosenIds, double totalCost, double coveredWeight, double totalWeight,
                         List<Integer> uncovered, List<Integer> uncoverable) {

        public double coverageFraction() {
            return totalWeight == 0 ? 1.0 : coveredWeight / totalWeight;
        }
    }

    // ------------------------------------------------------------------ greedy set cover

    public static Result greedyCover(double[] weights, List<CandidateSet> sets) {
        validate(weights, sets);
        int n = weights.length;
        BitSet coverable = coverable(n, sets);
        BitSet covered = new BitSet(n);
        boolean[] used = new boolean[sets.size()];
        List<Integer> chosen = new ArrayList<>();
        double cost = 0;
        while (covered.cardinality() < coverable.cardinality()) {
            int best = -1;
            double bestRatio = Double.POSITIVE_INFINITY;
            for (int s = 0; s < sets.size(); s++) {
                if (used[s]) {
                    continue;
                }
                int fresh = 0;
                for (int e : sets.get(s).elements()) {
                    if (!covered.get(e)) {
                        fresh++;
                    }
                }
                if (fresh == 0) {
                    continue;
                }
                double ratio = sets.get(s).cost() / fresh;
                if (ratio < bestRatio) {
                    bestRatio = ratio;
                    best = s;
                }
            }
            if (best < 0) {
                break; // cannot happen while coverable elements remain, kept as a guard
            }
            used[best] = true;
            chosen.add(sets.get(best).id());
            cost += sets.get(best).cost();
            for (int e : sets.get(best).elements()) {
                covered.set(e);
            }
        }
        return result(weights, chosen, cost, covered, coverable);
    }

    // ------------------------------------------------------------------ budgeted max coverage

    public static Result greedyBudget(double[] weights, List<CandidateSet> sets, double budget) {
        validate(weights, sets);
        if (!(budget >= 0) || Double.isInfinite(budget)) {
            throw new IllegalArgumentException("Budget must be a non-negative number");
        }
        int n = weights.length;
        BitSet coverable = coverable(n, sets);

        // (a) ratio greedy: most new weight per minute among sets that still fit
        BitSet covered = new BitSet(n);
        boolean[] used = new boolean[sets.size()];
        List<Integer> chosen = new ArrayList<>();
        double spent = 0;
        while (true) {
            int best = -1;
            double bestRatio = 0;
            for (int s = 0; s < sets.size(); s++) {
                CandidateSet cs = sets.get(s);
                if (used[s] || spent + cs.cost() > budget + 1e-9) {
                    continue;
                }
                double gain = newWeight(weights, cs, covered);
                if (gain <= 0) {
                    continue;
                }
                double ratio = gain / cs.cost();
                if (ratio > bestRatio) {
                    bestRatio = ratio;
                    best = s;
                }
            }
            if (best < 0) {
                break;
            }
            used[best] = true;
            chosen.add(sets.get(best).id());
            spent += sets.get(best).cost();
            for (int e : sets.get(best).elements()) {
                covered.set(e);
            }
        }
        double greedyWeight = weightOf(weights, covered);

        // (b) best single affordable set
        int bestSingle = -1;
        double bestSingleWeight = 0;
        for (int s = 0; s < sets.size(); s++) {
            if (sets.get(s).cost() <= budget + 1e-9) {
                double w = newWeight(weights, sets.get(s), new BitSet(n));
                if (w > bestSingleWeight) {
                    bestSingleWeight = w;
                    bestSingle = s;
                }
            }
        }
        if (bestSingle >= 0 && bestSingleWeight > greedyWeight) {
            BitSet single = new BitSet(n);
            for (int e : sets.get(bestSingle).elements()) {
                single.set(e);
            }
            return result(weights, List.of(sets.get(bestSingle).id()), sets.get(bestSingle).cost(), single, coverable);
        }
        return result(weights, chosen, spent, covered, coverable);
    }

    // ------------------------------------------------------------------ exact (brute force)

    /** Minimum-cost cover of all coverable elements by trying every subset. O(2^s * s) with s ≤ 22. */
    public static Result exactCover(double[] weights, List<CandidateSet> sets) {
        validate(weights, sets);
        requireSmall(sets);
        int n = weights.length;
        BitSet coverable = coverable(n, sets);
        Subsets all = Subsets.enumerate(weights.length, sets);
        long target = toMask(coverable);
        int bestMask = -1;
        double bestCost = Double.POSITIVE_INFINITY;
        for (int m = 0; m < all.size; m++) {
            if ((all.cover[m] & target) == target && all.cost[m] < bestCost) {
                bestCost = all.cost[m];
                bestMask = m;
            }
        }
        return fromMask(weights, sets, bestMask, coverable);
    }

    /** Maximum covered weight within the budget by trying every subset. O(2^s * s) with s ≤ 22. */
    public static Result exactBudget(double[] weights, List<CandidateSet> sets, double budget) {
        validate(weights, sets);
        requireSmall(sets);
        int n = weights.length;
        BitSet coverable = coverable(n, sets);
        Subsets all = Subsets.enumerate(weights.length, sets);
        int bestMask = 0;
        double bestWeight = -1, bestCost = Double.POSITIVE_INFINITY;
        for (int m = 0; m < all.size; m++) {
            if (all.cost[m] > budget + 1e-9) {
                continue;
            }
            double w = maskWeight(weights, all.cover[m]);
            if (w > bestWeight + 1e-12 || (Math.abs(w - bestWeight) <= 1e-12 && all.cost[m] < bestCost)) {
                bestWeight = w;
                bestCost = all.cost[m];
                bestMask = m;
            }
        }
        return fromMask(weights, sets, bestMask, coverable);
    }

    /** Cost and coverage of every subset, built incrementally from the subset minus its lowest set. */
    private static final class Subsets {
        final int size;
        final double[] cost;
        final long[] cover;

        private Subsets(int size) {
            this.size = size;
            this.cost = new double[size];
            this.cover = new long[size];
        }

        static Subsets enumerate(int elements, List<CandidateSet> sets) {
            if (elements > 64) {
                throw new IllegalArgumentException("Exact solver supports at most 64 elements");
            }
            int s = sets.size();
            long[] setMask = new long[s];
            for (int i = 0; i < s; i++) {
                for (int e : sets.get(i).elements()) {
                    setMask[i] |= 1L << e;
                }
            }
            Subsets t = new Subsets(1 << s);
            for (int m = 1; m < t.size; m++) {
                int low = Integer.numberOfTrailingZeros(m);
                int rest = m & (m - 1);
                t.cost[m] = t.cost[rest] + sets.get(low).cost();
                t.cover[m] = t.cover[rest] | setMask[low];
            }
            return t;
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void validate(double[] weights, List<CandidateSet> sets) {
        for (double w : weights) {
            if (!(w >= 0) || Double.isInfinite(w)) {
                throw new IllegalArgumentException("Element weights must be non-negative numbers");
            }
        }
        for (CandidateSet s : sets) {
            for (int e : s.elements()) {
                if (e < 0 || e >= weights.length) {
                    throw new IllegalArgumentException("Set " + s.id() + " references unknown element " + e);
                }
            }
        }
    }

    private static void requireSmall(List<CandidateSet> sets) {
        if (sets.size() > EXACT_LIMIT) {
            throw new IllegalArgumentException("Exact solver is limited to " + EXACT_LIMIT + " sets (2^s subsets)");
        }
    }

    private static BitSet coverable(int n, List<CandidateSet> sets) {
        BitSet c = new BitSet(n);
        for (CandidateSet s : sets) {
            for (int e : s.elements()) {
                c.set(e);
            }
        }
        return c;
    }

    private static double newWeight(double[] weights, CandidateSet s, BitSet covered) {
        double g = 0;
        BitSet seen = new BitSet();
        for (int e : s.elements()) {
            if (!covered.get(e) && !seen.get(e)) {
                g += weights[e];
                seen.set(e);
            }
        }
        return g;
    }

    private static double weightOf(double[] weights, BitSet covered) {
        double w = 0;
        for (int e = covered.nextSetBit(0); e >= 0; e = covered.nextSetBit(e + 1)) {
            w += weights[e];
        }
        return w;
    }

    private static double maskWeight(double[] weights, long mask) {
        double w = 0;
        while (mask != 0) {
            int e = Long.numberOfTrailingZeros(mask);
            w += weights[e];
            mask &= mask - 1;
        }
        return w;
    }

    private static long toMask(BitSet b) {
        long m = 0;
        for (int e = b.nextSetBit(0); e >= 0; e = b.nextSetBit(e + 1)) {
            m |= 1L << e;
        }
        return m;
    }

    private static Result fromMask(double[] weights, List<CandidateSet> sets, int mask, BitSet coverable) {
        BitSet covered = new BitSet(weights.length);
        List<Integer> chosen = new ArrayList<>();
        double cost = 0;
        if (mask > 0) {
            for (int i = 0; i < sets.size(); i++) {
                if ((mask & (1 << i)) != 0) {
                    chosen.add(sets.get(i).id());
                    cost += sets.get(i).cost();
                    for (int e : sets.get(i).elements()) {
                        covered.set(e);
                    }
                }
            }
        }
        return result(weights, chosen, cost, covered, coverable);
    }

    private static Result result(double[] weights, List<Integer> chosen, double cost, BitSet covered, BitSet coverable) {
        List<Integer> uncovered = new ArrayList<>();
        List<Integer> uncoverable = new ArrayList<>();
        double total = 0;
        for (int e = 0; e < weights.length; e++) {
            total += weights[e];
            if (!covered.get(e)) {
                uncovered.add(e);
                if (!coverable.get(e)) {
                    uncoverable.add(e);
                }
            }
        }
        return new Result(List.copyOf(chosen), cost, weightOf(weights, covered), total, uncovered, uncoverable);
    }
}
