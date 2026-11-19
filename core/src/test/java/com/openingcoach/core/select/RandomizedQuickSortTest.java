package com.openingcoach.core.select;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Correctness tests for {@link RandomizedQuickSort} (in addition to the one in RandomizedSelectTest). */
class RandomizedQuickSortTest {

    private static final Comparator<Integer> NAT = Comparator.naturalOrder();

    private static List<Integer> randomList(Random r, int n, int bound) {
        return new ArrayList<>(IntStream.range(0, n).map(i -> r.nextInt(bound)).boxed().toList());
    }

    @Test
    void agreesWithCollectionsSortOnManyRandomInputs() {
        Random data = new Random(21);
        RandomizedQuickSort qs = new RandomizedQuickSort(5);
        for (int trial = 0; trial < 300; trial++) {
            List<Integer> xs = randomList(data, data.nextInt(200), 50); // many duplicates
            List<Integer> expected = new ArrayList<>(xs);
            Collections.sort(expected);
            qs.sort(xs, NAT);
            assertEquals(expected, xs, "trial " + trial);
        }
    }

    @Test
    void boundaryInputs() {
        RandomizedQuickSort qs = new RandomizedQuickSort(1);
        List<Integer> one = new ArrayList<>(List.of(42));
        qs.sort(one, NAT);
        assertEquals(List.of(42), one);
        List<Integer> two = new ArrayList<>(List.of(2, 1));
        qs.sort(two, NAT);
        assertEquals(List.of(1, 2), two);
        List<Integer> extremes = new ArrayList<>(List.of(Integer.MAX_VALUE, 0, Integer.MIN_VALUE, -1));
        qs.sort(extremes, NAT);
        assertEquals(List.of(Integer.MIN_VALUE, -1, 0, Integer.MAX_VALUE), extremes);
    }

    @Test
    void allEqualKeysTakeLinearComparisons() {
        // three-way partitioning puts every element in the "equal" block in one pass
        int n = 100_000;
        List<Integer> same = new ArrayList<>(Collections.nCopies(n, 7));
        RandomizedQuickSort qs = new RandomizedQuickSort(2);
        qs.sort(same, NAT);
        assertEquals(n, qs.comparisons());
    }

    @Test
    void sortedAndReversedInputsStayNearTwoNLnN() {
        // randomness is in the pivot, so already-ordered data is not a bad case
        int n = 50_000;
        for (boolean reversed : new boolean[] {false, true}) {
            List<Integer> xs = new ArrayList<>(IntStream.range(0, n).boxed().toList());
            if (reversed) {
                Collections.reverse(xs);
            }
            RandomizedQuickSort qs = new RandomizedQuickSort(3);
            qs.sort(xs, NAT);
            assertEquals(IntStream.range(0, n).boxed().toList(), xs);
            double ratio = qs.comparisons() / (2 * n * Math.log(n));
            assertTrue(ratio > 0.6 && ratio < 1.4, "reversed=" + reversed + " ratio=" + ratio);
        }
    }

    @Test
    void customComparatorSortsDescendingByScoreAndKeepsAllItems() {
        // the way the app uses it: order weak lines by points lost, largest first
        record Line(String name, double lost) {
        }
        List<Line> lines = new ArrayList<>(List.of(new Line("a", 1.5), new Line("b", 5.6), new Line("c", 0.2),
                new Line("d", 5.6), new Line("e", 3.9)));
        new RandomizedQuickSort(8).sort(lines, Comparator.comparingDouble(Line::lost).reversed());
        assertEquals(5, lines.size());
        for (int i = 1; i < lines.size(); i++) {
            assertTrue(lines.get(i - 1).lost() >= lines.get(i).lost());
        }
        assertEquals(List.of("c"), List.of(lines.get(4).name()));
    }
}
