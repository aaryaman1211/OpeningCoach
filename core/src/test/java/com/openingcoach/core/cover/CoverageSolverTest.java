package com.openingcoach.core.cover;

import com.openingcoach.core.cover.CoverageSolver.CandidateSet;
import com.openingcoach.core.cover.CoverageSolver.Result;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverageSolverTest {

    private static CandidateSet set(int id, double cost, int... elems) {
        return new CandidateSet(id, cost, elems);
    }

    @Test
    void greedyCoverCoversEverythingCoverable() {
        double[] w = {1, 1, 1, 1, 1};
        List<CandidateSet> sets = List.of(set(0, 3, 0, 1, 2), set(1, 2, 2, 3), set(2, 1, 4), set(3, 10, 0, 1, 2, 3, 4));
        Result r = CoverageSolver.greedyCover(w, sets);
        assertTrue(r.uncovered().isEmpty());
        assertEquals(6.0, r.totalCost(), 1e-9); // picks set 0 (1 per element), then set 2, then set 1
        assertEquals(1.0, r.coverageFraction(), 1e-9);
    }

    /** Classic instance where greedy is suboptimal: the bound matters, so we never call it optimal. */
    @Test
    void greedyCanBeWorseThanOptimalButWithinHarmonicBound() {
        // Elements 0..5. Optimal: two sets {0,1,2} and {3,4,5} at cost 1 each (total 2).
        // Greedy is lured by {0,1,3,4} costing 1.2 (0.3 per element, cheaper than 1/3), then must
        // still buy both other sets for the leftovers: 3.2 in total.
        double[] w = {1, 1, 1, 1, 1, 1};
        List<CandidateSet> sets = List.of(set(0, 1, 0, 1, 2), set(1, 1, 3, 4, 5), set(2, 1.2, 0, 1, 3, 4));
        Result g = CoverageSolver.greedyCover(w, sets);
        Result opt = CoverageSolver.exactCover(w, sets);
        assertEquals(2.0, opt.totalCost(), 1e-9);
        assertTrue(g.totalCost() > opt.totalCost());
        double hd = 1 + 1.0 / 2 + 1.0 / 3 + 1.0 / 4; // H(4), largest set size 4
        assertTrue(g.totalCost() <= hd * opt.totalCost());
    }

    @Test
    void uncoverableElementsAreReportedNotIgnored() {
        double[] w = {2, 3, 5};
        Result r = CoverageSolver.greedyCover(w, List.of(set(0, 1, 0, 1)));
        assertEquals(List.of(2), r.uncoverable());
        assertEquals(List.of(2), r.uncovered());
        assertEquals(0.5, r.coverageFraction(), 1e-9);
    }

    @Test
    void budgetIsNeverExceededAndZeroBudgetChoosesNothing() {
        double[] w = {5, 4, 3, 2, 1};
        List<CandidateSet> sets = List.of(set(0, 4, 0), set(1, 3, 1, 2), set(2, 2, 3, 4), set(3, 6, 0, 1, 2));
        for (double budget : new double[]{0, 1, 2, 3, 5, 7, 100}) {
            Result r = CoverageSolver.greedyBudget(w, sets, budget);
            assertTrue(r.totalCost() <= budget + 1e-9, "budget " + budget);
        }
        assertTrue(CoverageSolver.greedyBudget(w, sets, 0).chosenIds().isEmpty());
    }

    /** The "best single set" fallback is what rescues the guarantee with unequal costs. */
    @Test
    void bestSingleSetBeatsRatioGreedyWhenItShould() {
        double[] w = {1, 100};
        // ratio greedy takes the tiny set {0} (ratio 1/0.05 = 20 > 100/10), then cannot afford {1}
        List<CandidateSet> sets = List.of(set(0, 0.05, 0), set(1, 10, 1));
        Result r = CoverageSolver.greedyBudget(w, sets, 10);
        assertEquals(List.of(1), r.chosenIds());
        assertEquals(100.0, r.coveredWeight(), 1e-9);
    }

    /** Random small instances: greedy vs brute force, checking the proven bounds hold. */
    @Test
    void randomInstancesRespectApproximationBounds() {
        Random rnd = new Random(2024);
        double worstBudgetRatio = 1;
        for (int t = 0; t < 300; t++) {
            int n = 4 + rnd.nextInt(12), s = 2 + rnd.nextInt(10);
            double[] w = new double[n];
            for (int i = 0; i < n; i++) {
                w[i] = 1 + rnd.nextInt(10);
            }
            List<CandidateSet> sets = new ArrayList<>();
            int maxSize = 0;
            for (int j = 0; j < s; j++) {
                Set<Integer> el = new HashSet<>();
                int size = 1 + rnd.nextInt(Math.max(1, n / 2));
                while (el.size() < size) {
                    el.add(rnd.nextInt(n));
                }
                maxSize = Math.max(maxSize, size);
                sets.add(new CandidateSet(j, 1 + rnd.nextInt(20), el.stream().mapToInt(Integer::intValue).toArray()));
            }
            Result g = CoverageSolver.greedyCover(w, sets);
            Result o = CoverageSolver.exactCover(w, sets);
            double h = 0;
            for (int i = 1; i <= maxSize; i++) {
                h += 1.0 / i;
            }
            assertEquals(o.uncovered(), g.uncovered());
            assertTrue(g.totalCost() <= h * o.totalCost() + 1e-9, "set cover bound, trial " + t);

            double budget = 5 + rnd.nextInt(30);
            Result gb = CoverageSolver.greedyBudget(w, sets, budget);
            Result ob = CoverageSolver.exactBudget(w, sets, budget);
            assertTrue(gb.coveredWeight() <= ob.coveredWeight() + 1e-9);
            if (ob.coveredWeight() > 0) {
                double ratio = gb.coveredWeight() / ob.coveredWeight();
                worstBudgetRatio = Math.min(worstBudgetRatio, ratio);
                assertTrue(ratio >= (1 - 1 / Math.E) / 2 - 1e-9, "budgeted bound, trial " + t);
            }
        }
        assertTrue(worstBudgetRatio > 0.316);
    }

    @Test
    void invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> set(0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> set(0, -2, 1));
        assertThrows(IllegalArgumentException.class,
                () -> CoverageSolver.greedyCover(new double[]{1}, List.of(set(0, 1, 5))));
        assertThrows(IllegalArgumentException.class,
                () -> CoverageSolver.greedyCover(new double[]{-1}, List.of(set(0, 1, 0))));
        assertThrows(IllegalArgumentException.class,
                () -> CoverageSolver.greedyBudget(new double[]{1}, List.of(set(0, 1, 0)), -5));
        List<CandidateSet> many = new ArrayList<>();
        for (int i = 0; i < 23; i++) {
            many.add(set(i, 1, 0));
        }
        assertThrows(IllegalArgumentException.class, () -> CoverageSolver.exactCover(new double[]{1}, many));
    }

    @Test
    void emptyInstance() {
        Result r = CoverageSolver.greedyCover(new double[0], List.of());
        assertTrue(r.chosenIds().isEmpty());
        assertEquals(1.0, r.coverageFraction(), 1e-9);
    }
}
