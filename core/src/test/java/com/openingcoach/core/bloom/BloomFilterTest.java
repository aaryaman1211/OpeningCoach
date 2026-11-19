package com.openingcoach.core.bloom;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BloomFilterTest {

    @Test
    void neverGivesFalseNegatives() {
        BloomFilter f = BloomFilter.forExpected(100_000, 0.01);
        SplittableRandom rnd = new SplittableRandom(1);
        long[] keys = new long[100_000];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = rnd.nextLong();
            f.add(keys[i]);
        }
        for (long k : keys) {
            assertTrue(f.mightContain(k));
        }
    }

    /**
     * Statistical test: measured false-positive rate on 200k non-members must lie inside a
     * 5-sigma binomial interval around the theoretical (1 - e^(-kn/m))^k. With p ≈ 0.01 and
     * N = 200k, sigma ≈ 0.00022, so a broken hash function fails this immediately.
     */
    @Test
    void falsePositiveRateMatchesTheory() {
        int n = 50_000;
        BloomFilter f = BloomFilter.forExpected(n, 0.01);
        SplittableRandom rnd = new SplittableRandom(7);
        Set<Long> members = new HashSet<>();
        while (members.size() < n) {
            long k = rnd.nextLong();
            members.add(k);
            f.add(k);
        }
        int trials = 200_000, fp = 0, tested = 0;
        while (tested < trials) {
            long k = rnd.nextLong();
            if (members.contains(k)) {
                continue;
            }
            tested++;
            if (f.mightContain(k)) {
                fp++;
            }
        }
        double p = f.expectedFpp();
        double observed = (double) fp / trials;
        double sigma = Math.sqrt(p * (1 - p) / trials);
        assertTrue(Math.abs(observed - p) < 5 * sigma,
                "observed " + observed + " vs theoretical " + p + " (sigma " + sigma + ")");
        assertEquals(0.5, f.fillRatio(), 0.02, "optimal k leaves about half the bits set");
    }

    @Test
    void sizingFormulasMatchTheTextbook() {
        // 1% target -> about 9.59 bits per key and k = 7
        long m = BloomFilter.optimalBits(1_000_000, 0.01);
        assertEquals(9.585, m / 1_000_000.0, 0.01);
        assertEquals(7, BloomFilter.optimalHashes(1_000_000, m));
        // 0.1% target -> about 14.38 bits per key and k = 10
        long m2 = BloomFilter.optimalBits(1_000_000, 0.001);
        assertEquals(14.378, m2 / 1_000_000.0, 0.01);
        assertEquals(10, BloomFilter.optimalHashes(1_000_000, m2));
    }

    @Test
    void emptyFilterContainsNothing() {
        BloomFilter f = new BloomFilter(1024, 3);
        SplittableRandom rnd = new SplittableRandom(3);
        for (int i = 0; i < 1000; i++) {
            assertFalse(f.mightContain(rnd.nextLong()));
        }
        assertEquals(0.0, f.expectedFpp());
    }

    @Test
    void tinyFilterSaturatesButStaysCorrect() {
        // Boundary: far more keys than bits -> every query says "maybe", members still found.
        BloomFilter f = new BloomFilter(64, 2);
        for (long k = 0; k < 10_000; k++) {
            f.add(k);
        }
        assertTrue(f.mightContain(5));
        assertTrue(f.expectedFpp() > 0.99);
    }

    @Test
    void rejectsInvalidParameters() {
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(0, 3));
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(100, 0));
        assertThrows(IllegalArgumentException.class, () -> BloomFilter.forExpected(0, 0.01));
        assertThrows(IllegalArgumentException.class, () -> BloomFilter.forExpected(100, 0));
        assertThrows(IllegalArgumentException.class, () -> BloomFilter.forExpected(100, 1.5));
    }

    @Test
    void serialisationRoundTrip() throws IOException {
        BloomFilter f = BloomFilter.forExpected(1000, 0.01);
        for (long k = 0; k < 1000; k++) {
            f.add(k * 31);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        f.writeTo(out);
        BloomFilter g = BloomFilter.readFrom(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(f.bitCount(), g.bitCount());
        assertEquals(f.hashCount(), g.hashCount());
        assertEquals(f.insertedCount(), g.insertedCount());
        for (long k = 0; k < 5000; k++) {
            assertEquals(f.mightContain(k), g.mightContain(k));
        }
    }
}
