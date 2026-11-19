package com.openingcoach.core.select;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Randomized Quickselect (Unit 6 / CO5: randomized algorithm).
 *
 * <p>Finds the element of rank k (0-based) without sorting. Each round picks a pivot uniformly
 * at random and does a three-way partition (&lt; pivot | = pivot | &gt; pivot), then continues only
 * in the part that contains rank k. Expected comparisons are O(n) — about 2n + 2k ln(n/k) +
 * 2(n-k) ln(n/(n-k)), at most ~3.39n for the median — for every input, because the
 * randomness is in the algorithm, not the data. The worst case is still O(n^2), but it needs
 * consistently unlucky pivots, with vanishing probability.
 *
 * <p>The three-way partition matters here: opening scores have many ties, and a two-way
 * partition degrades badly on equal keys.
 */
public final class RandomizedSelect {

    private final Random random;
    private long comparisons;

    public RandomizedSelect(Random random) {
        this.random = random;
    }

    public RandomizedSelect(long seed) {
        this(new Random(seed));
    }

    /** Comparisons made since construction or the last {@link #resetComparisons()}. */
    public long comparisons() {
        return comparisons;
    }

    public void resetComparisons() {
        comparisons = 0;
    }

    /** Element of rank k (0 = smallest) under {@code cmp}. Does not modify {@code items}. */
    public <T> T select(List<T> items, int k, Comparator<? super T> cmp) {
        Object[] a = items.toArray();
        return selectInPlace(a, k, cmp);
    }

    /**
     * The k smallest elements (in no particular order). After selecting rank k-1, everything to
     * its left in the partitioned array is no larger, so the prefix is the answer. Expected O(n).
     */
    public <T> List<T> smallestK(List<T> items, int k, Comparator<? super T> cmp) {
        if (k < 0 || k > items.size()) {
            throw new IllegalArgumentException("k must be in 0.." + items.size());
        }
        if (k == 0) {
            return new ArrayList<>();
        }
        Object[] a = items.toArray();
        selectInPlace(a, k - 1, cmp);
        List<T> out = new ArrayList<>(k);
        for (int i = 0; i < k; i++) {
            @SuppressWarnings("unchecked")
            T t = (T) a[i];
            out.add(t);
        }
        return out;
    }

    /** Nearest-rank percentile (p in [0, 100]) of the values, in expected O(n). */
    public double percentile(double[] values, double p) {
        if (values.length == 0) {
            throw new IllegalArgumentException("No values");
        }
        if (!(p >= 0 && p <= 100)) {
            throw new IllegalArgumentException("Percentile must be in [0, 100]");
        }
        int rank = (int) Math.ceil(p / 100.0 * values.length) - 1;
        rank = Math.max(0, Math.min(values.length - 1, rank));
        Double[] boxed = Arrays.stream(values).boxed().toArray(Double[]::new);
        return selectInPlace(boxed, rank, Comparator.<Double>naturalOrder());
    }

    @SuppressWarnings("unchecked")
    private <T> T selectInPlace(Object[] a, int k, Comparator<? super T> cmp) {
        if (a.length == 0) {
            throw new IllegalArgumentException("Cannot select from an empty list");
        }
        if (k < 0 || k >= a.length) {
            throw new IllegalArgumentException("k must be in 0.." + (a.length - 1));
        }
        int lo = 0, hi = a.length - 1;
        while (lo < hi) {
            T pivot = (T) a[lo + random.nextInt(hi - lo + 1)];
            // Dutch national flag partition of a[lo..hi]
            int lt = lo, i = lo, gt = hi;
            while (i <= gt) {
                int c = cmp.compare((T) a[i], pivot);
                comparisons++;
                if (c < 0) {
                    swap(a, lt++, i++);
                } else if (c > 0) {
                    swap(a, i, gt--);
                } else {
                    i++;
                }
            }
            if (k < lt) {
                hi = lt - 1;
            } else if (k > gt) {
                lo = gt + 1;
            } else {
                return (T) a[k]; // rank k falls in the block equal to the pivot
            }
        }
        return (T) a[k];
    }

    private static void swap(Object[] a, int i, int j) {
        Object t = a[i];
        a[i] = a[j];
        a[j] = t;
    }
}
