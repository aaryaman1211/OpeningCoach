package com.openingcoach.core.select;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomizedSelectTest {

    private static final Comparator<Integer> NAT = Comparator.naturalOrder();

    @Test
    void agreesWithSortingForEveryRankOnRandomInputs() {
        Random data = new Random(11);
        for (int trial = 0; trial < 200; trial++) {
            int n = 1 + data.nextInt(60);
            List<Integer> xs = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                xs.add(data.nextInt(20)); // many duplicates on purpose
            }
            List<Integer> sorted = new ArrayList<>(xs);
            Collections.sort(sorted);
            RandomizedSelect sel = new RandomizedSelect(trial);
            for (int k = 0; k < n; k++) {
                assertEquals(sorted.get(k), sel.select(xs, k, NAT), "trial " + trial + " k " + k);
            }
        }
    }

    @Test
    void smallestKReturnsExactlyTheKSmallest() {
        Random data = new Random(5);
        List<Integer> xs = IntStream.range(0, 1000).map(i -> data.nextInt(100)).boxed().toList();
        List<Integer> sorted = new ArrayList<>(xs);
        Collections.sort(sorted);
        for (int k : new int[]{0, 1, 10, 500, 999, 1000}) {
            List<Integer> got = new RandomizedSelect(k).smallestK(xs, k, NAT);
            Collections.sort(got);
            assertEquals(sorted.subList(0, k), got, "k = " + k);
        }
    }

    @Test
    void allEqualElements() {
        List<Integer> same = Collections.nCopies(10_000, 7);
        RandomizedSelect sel = new RandomizedSelect(1);
        assertEquals(7, sel.select(same, 5000, NAT));
        assertTrue(sel.comparisons() <= 10_000, "three-way partition finishes in one pass on equal keys");
    }

    @Test
    void singleElementAndBoundaries() {
        RandomizedSelect sel = new RandomizedSelect(2);
        assertEquals(42, sel.select(List.of(42), 0, NAT));
        assertThrows(IllegalArgumentException.class, () -> sel.select(List.of(), 0, NAT));
        assertThrows(IllegalArgumentException.class, () -> sel.select(List.of(1, 2), 2, NAT));
        assertThrows(IllegalArgumentException.class, () -> sel.select(List.of(1, 2), -1, NAT));
        assertThrows(IllegalArgumentException.class, () -> sel.smallestK(List.of(1, 2), 3, NAT));
    }

    /** Sorted input is the worst case for a fixed first-element pivot; random pivots stay linear. */
    @Test
    void sortedInputStaysLinearInExpectation() {
        int n = 100_000;
        List<Integer> sorted = IntStream.range(0, n).boxed().toList();
        long total = 0;
        int runs = 20;
        for (int s = 0; s < runs; s++) {
            RandomizedSelect sel = new RandomizedSelect(s);
            assertEquals(n / 2, sel.select(sorted, n / 2, NAT));
            total += sel.comparisons();
        }
        double perElement = (double) total / runs / n;
        // theory: about 3.39 n comparisons for the median (2 + 2 ln 2); allow slack for variance
        assertTrue(perElement > 2.0 && perElement < 4.5, "comparisons per element = " + perElement);
    }

    @Test
    void percentileUsesNearestRank() {
        double[] v = {15, 20, 35, 40, 50};
        RandomizedSelect sel = new RandomizedSelect(3);
        assertEquals(20, sel.percentile(v, 30));
        assertEquals(35, sel.percentile(v, 50));
        assertEquals(50, sel.percentile(v, 100));
        assertEquals(15, sel.percentile(v, 0));
        assertThrows(IllegalArgumentException.class, () -> sel.percentile(v, 101));
        assertThrows(IllegalArgumentException.class, () -> sel.percentile(new double[0], 50));
    }

    @Test
    void quickSortSortsAndCountsAboutTwoNLnN() {
        Random data = new Random(9);
        int n = 50_000;
        List<Integer> xs = new ArrayList<>(IntStream.range(0, n).map(i -> data.nextInt()).boxed().toList());
        List<Integer> expected = new ArrayList<>(xs);
        Collections.sort(expected);
        RandomizedQuickSort qs = new RandomizedQuickSort(4);
        qs.sort(xs, NAT);
        assertEquals(expected, xs);
        double ratio = qs.comparisons() / (2 * n * Math.log(n));
        assertTrue(ratio > 0.7 && ratio < 1.3, "comparisons / 2n ln n = " + ratio);

        List<Integer> empty = new ArrayList<>();
        qs.sort(empty, NAT);
        assertTrue(empty.isEmpty());
        List<Integer> dupes = new ArrayList<>(Arrays.asList(3, 1, 3, 1, 3));
        qs.sort(dupes, NAT);
        assertEquals(List.of(1, 1, 3, 3, 3), dupes);
    }
}
