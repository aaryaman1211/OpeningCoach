package com.openingcoach.core.select;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Randomized QuickSort with three-way partitioning (Unit 6 / CO5).
 *
 * <p>Expected comparisons about 2n ln n ≈ 1.39 n log2 n on any input; recursing into the
 * smaller part and looping on the larger keeps the stack at O(log n). Used to order the short
 * lists shown in the UI (after Quickselect has cut them down to k items).
 */
public final class RandomizedQuickSort {

    private final Random random;
    private long comparisons;

    public RandomizedQuickSort(Random random) {
        this.random = random;
    }

    public RandomizedQuickSort(long seed) {
        this(new Random(seed));
    }

    public long comparisons() {
        return comparisons;
    }

    public void resetComparisons() {
        comparisons = 0;
    }

    /** Sorts the list in place. */
    public <T> void sort(List<T> list, Comparator<? super T> cmp) {
        Object[] a = list.toArray();
        sort(a, 0, a.length - 1, cmp);
        for (int i = 0; i < a.length; i++) {
            @SuppressWarnings("unchecked")
            T t = (T) a[i];
            list.set(i, t);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void sort(Object[] a, int lo, int hi, Comparator<? super T> cmp) {
        while (lo < hi) {
            T pivot = (T) a[lo + random.nextInt(hi - lo + 1)];
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
            if (lt - lo < hi - gt) {
                sort(a, lo, lt - 1, cmp);
                lo = gt + 1;
            } else {
                sort(a, gt + 1, hi, cmp);
                hi = lt - 1;
            }
        }
    }

    private static void swap(Object[] a, int i, int j) {
        Object t = a[i];
        a[i] = a[j];
        a[j] = t;
    }
}
