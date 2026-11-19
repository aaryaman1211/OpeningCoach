package com.openingcoach.core.bloom;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Bloom filter over 64-bit keys (Unit 6 / CO5: probabilistic data structure).
 *
 * <p>An m-bit array and k hash functions. {@link #add} sets k bits; {@link #mightContain}
 * returns false only if the key was definitely never added (no false negatives), and true
 * for every added key plus a small fraction of other keys (false positives). With n keys
 * inserted the false-positive probability is about (1 - e^(-kn/m))^k, minimised at
 * k = (m/n) ln 2. For a target rate p this needs m = -n ln p / (ln 2)^2 bits,
 * i.e. about 9.6 bits per key at p = 1%.
 *
 * <p>The k indexes come from double hashing (Kirsch &amp; Mitzenmacher 2006):
 * g_i(x) = h1(x) + i * h2(x) mod m, which keeps the same asymptotic false-positive rate
 * while computing only two base hashes.
 *
 * <p>Every "might contain" answer must be confirmed against an exact index before it is
 * shown to the user as a fact.
 */
public final class BloomFilter {

    private static final double LN2 = Math.log(2);

    private final long[] words;
    private final long bits;
    private final int hashes;
    private long inserted;

    public BloomFilter(long bits, int hashes) {
        if (bits <= 0) {
            throw new IllegalArgumentException("Bit count must be positive");
        }
        if (bits > (long) Integer.MAX_VALUE * 64) {
            throw new IllegalArgumentException("Bit count too large");
        }
        if (hashes <= 0 || hashes > 64) {
            throw new IllegalArgumentException("Hash count must be in 1..64");
        }
        this.bits = bits;
        this.hashes = hashes;
        this.words = new long[(int) ((bits + 63) / 64)];
    }

    /** Sizes a filter for {@code expectedKeys} keys at false-positive rate {@code targetFpp}. */
    public static BloomFilter forExpected(long expectedKeys, double targetFpp) {
        if (expectedKeys <= 0) {
            throw new IllegalArgumentException("Expected key count must be positive");
        }
        if (!(targetFpp > 0 && targetFpp < 1)) {
            throw new IllegalArgumentException("Target false-positive rate must be in (0, 1)");
        }
        long m = optimalBits(expectedKeys, targetFpp);
        return new BloomFilter(m, optimalHashes(expectedKeys, m));
    }

    /** m = -n ln p / (ln 2)^2, rounded up. */
    public static long optimalBits(long n, double p) {
        return Math.max(64, (long) Math.ceil(-n * Math.log(p) / (LN2 * LN2)));
    }

    /** k = (m / n) ln 2, rounded, at least 1. */
    public static int optimalHashes(long n, long m) {
        return (int) Math.max(1, Math.min(64, Math.round((double) m / n * LN2)));
    }

    public void add(long key) {
        long h1 = mix(key);
        long h2 = mix(key ^ 0x9E3779B97F4A7C15L) | 1L; // odd, so the k probes differ
        for (int i = 0; i < hashes; i++) {
            long idx = Math.floorMod(h1 + i * h2, bits);
            words[(int) (idx >>> 6)] |= 1L << (idx & 63);
        }
        inserted++;
    }

    public boolean mightContain(long key) {
        long h1 = mix(key);
        long h2 = mix(key ^ 0x9E3779B97F4A7C15L) | 1L;
        for (int i = 0; i < hashes; i++) {
            long idx = Math.floorMod(h1 + i * h2, bits);
            if ((words[(int) (idx >>> 6)] & (1L << (idx & 63))) == 0) {
                return false; // a zero bit proves the key was never added
            }
        }
        return true;
    }

    /** Theoretical false-positive probability for the keys inserted so far: (1 - e^(-kn/m))^k. */
    public double expectedFpp() {
        return Math.pow(1 - Math.exp(-(double) hashes * inserted / bits), hashes);
    }

    /** Fraction of bits set; about 1 - e^(-kn/m), and 0.5 at the optimal k. */
    public double fillRatio() {
        long set = 0;
        for (long w : words) {
            set += Long.bitCount(w);
        }
        return (double) set / bits;
    }

    public long bitCount() {
        return bits;
    }

    public int hashCount() {
        return hashes;
    }

    public long insertedCount() {
        return inserted;
    }

    public long memoryBytes() {
        return words.length * 8L;
    }

    /** SplitMix64 finaliser: spreads the bits of the key so nearby keys hash far apart. */
    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public void writeTo(OutputStream out) throws IOException {
        DataOutputStream d = new DataOutputStream(out);
        d.writeInt(0xB10F11E7);
        d.writeLong(bits);
        d.writeInt(hashes);
        d.writeLong(inserted);
        for (long w : words) {
            d.writeLong(w);
        }
        d.flush();
    }

    public static BloomFilter readFrom(InputStream in) throws IOException {
        DataInputStream d = new DataInputStream(in);
        if (d.readInt() != 0xB10F11E7) {
            throw new IOException("Not a Bloom filter file");
        }
        BloomFilter f = new BloomFilter(d.readLong(), d.readInt());
        f.inserted = d.readLong();
        for (int i = 0; i < f.words.length; i++) {
            f.words[i] = d.readLong();
        }
        return f;
    }
}
