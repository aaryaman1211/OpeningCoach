package com.openingcoach.core.reference;

import com.openingcoach.core.Stats;
import com.openingcoach.core.bloom.BloomFilter;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Disk-backed reference book with an in-memory Bloom filter in front of it.
 *
 * <p>This mirrors how storage engines such as Cassandra and HBase use Bloom filters: the exact
 * data lives in sorted files on disk, and each lookup first asks the in-memory filter. A "no"
 * is definitive (Bloom filters have no false negatives), so the disk is never touched for
 * positions strong players never reached — which is most positions once a user leaves theory.
 * A "maybe" is always confirmed by binary search over the sorted file (the authoritative
 * check); a maybe that turns out absent is a measured false positive.
 *
 * <p>Each binary-search step is one positional read ("probe"); a lookup costs
 * O(log P) probes for P positions, while a Bloom rejection costs k in-memory bit tests.
 */
public final class ReferenceIndex implements ReferenceBook, Closeable {

    private static final int MAX_MOVES = 40;

    private final FileChannel positions;
    private final FileChannel moves;
    private final long positionCount;
    private final long moveCount;
    private final BloomFilter bloom;
    private final Properties meta;
    private volatile boolean bloomEnabled = true;

    private final AtomicLong lookups = new AtomicLong();
    private final AtomicLong bloomRejections = new AtomicLong();
    private final AtomicLong confirmedHits = new AtomicLong();
    private final AtomicLong falsePositives = new AtomicLong();
    private final AtomicLong diskProbes = new AtomicLong();

    private ReferenceIndex(Path dir) throws IOException {
        this.positions = FileChannel.open(dir.resolve("positions.bin"), StandardOpenOption.READ);
        this.moves = FileChannel.open(dir.resolve("moves.bin"), StandardOpenOption.READ);
        this.positionCount = positions.size() / ReferenceIndexBuilder.POSITION_RECORD;
        this.moveCount = moves.size() / ReferenceIndexBuilder.MOVE_RECORD;
        try (InputStream in = new BufferedInputStream(Files.newInputStream(dir.resolve("positions.bloom")))) {
            this.bloom = BloomFilter.readFrom(in);
        }
        this.meta = new Properties();
        Path metaFile = dir.resolve("meta.properties");
        if (Files.exists(metaFile)) {
            try (var r = Files.newBufferedReader(metaFile, StandardCharsets.UTF_8)) {
                meta.load(r);
            }
        }
    }

    public static ReferenceIndex open(Path dir) throws IOException {
        return new ReferenceIndex(dir);
    }

    public static boolean exists(Path dir) {
        return Files.exists(dir.resolve("positions.bin")) && Files.exists(dir.resolve("moves.bin"))
                && Files.exists(dir.resolve("positions.bloom"));
    }

    /** Turns the Bloom gate off, for the "without Bloom filter" benchmark baseline. */
    public void setBloomEnabled(boolean enabled) {
        this.bloomEnabled = enabled;
    }

    @Override
    public Optional<Stats> position(long key) {
        lookups.incrementAndGet();
        if (bloomEnabled && !bloom.mightContain(key)) {
            bloomRejections.incrementAndGet();
            return Optional.empty();
        }
        long idx = lowerBound(positions, positionCount, ReferenceIndexBuilder.POSITION_RECORD, key);
        if (idx < positionCount) {
            ByteBuffer rec = read(positions, idx * ReferenceIndexBuilder.POSITION_RECORD, ReferenceIndexBuilder.POSITION_RECORD);
            if (rec.getLong(0) == key) {
                confirmedHits.incrementAndGet();
                return Optional.of(new Stats(rec.getInt(8), rec.getInt(12), rec.getInt(16)));
            }
        }
        if (bloomEnabled) {
            falsePositives.incrementAndGet();
        }
        return Optional.empty();
    }

    @Override
    public List<RefMove> moves(long key) {
        if (bloomEnabled && !bloom.mightContain(key)) {
            return List.of();
        }
        long idx = lowerBound(moves, moveCount, ReferenceIndexBuilder.MOVE_RECORD, key);
        List<RefMove> out = new ArrayList<>();
        while (idx < moveCount && out.size() < MAX_MOVES) {
            ByteBuffer rec = read(moves, idx * ReferenceIndexBuilder.MOVE_RECORD, ReferenceIndexBuilder.MOVE_RECORD);
            if (rec.getLong(0) != key) {
                break;
            }
            byte[] san = new byte[8];
            rec.get(16, san);
            int len = 0;
            while (len < 8 && san[len] != 0) {
                len++;
            }
            out.add(new RefMove(new String(san, 0, len, StandardCharsets.US_ASCII), rec.getLong(8),
                    new Stats(rec.getInt(24), rec.getInt(28), rec.getInt(32))));
            idx++;
        }
        return out;
    }

    /** First record index whose key is ≥ {@code key}; each step is one disk probe. */
    private long lowerBound(FileChannel ch, long count, int recordSize, long key) {
        long lo = 0, hi = count;
        while (lo < hi) {
            long mid = (lo + hi) >>> 1;
            long midKey = read(ch, mid * recordSize, 8).getLong(0);
            if (midKey < key) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private ByteBuffer read(FileChannel ch, long offset, int length) {
        diskProbes.incrementAndGet();
        ByteBuffer buf = ByteBuffer.allocate(length);
        try {
            while (buf.hasRemaining()) {
                int n = ch.read(buf, offset + buf.position());
                if (n < 0) {
                    throw new IOException("Unexpected end of index file");
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        buf.flip();
        return buf;
    }

    // ------------------------------------------------------------------ statistics

    /** Snapshot of the counters and filter parameters for the "under the hood" view. */
    public record Metrics(long positions, long moves, long bloomBits, int bloomHashes, long bloomBytes,
                          double bloomExpectedFpp, double bloomFillRatio, long lookups, long bloomRejections,
                          long confirmedHits, long falsePositives, long diskProbes, Properties meta) {

        /** Measured false-positive rate among lookups of keys that are not in the index. */
        public double observedFpp() {
            long negatives = bloomRejections + falsePositives;
            return negatives == 0 ? 0 : (double) falsePositives / negatives;
        }
    }

    public Metrics metrics() {
        return new Metrics(positionCount, moveCount, bloom.bitCount(), bloom.hashCount(), bloom.memoryBytes(),
                bloom.expectedFpp(), bloom.fillRatio(), lookups.get(), bloomRejections.get(), confirmedHits.get(),
                falsePositives.get(), diskProbes.get(), meta);
    }

    public BloomFilter bloom() {
        return bloom;
    }

    public long positionCount() {
        return positionCount;
    }

    public long diskProbes() {
        return diskProbes.get();
    }

    public void resetCounters() {
        lookups.set(0);
        bloomRejections.set(0);
        confirmedHits.set(0);
        falsePositives.set(0);
        diskProbes.set(0);
    }

    @Override
    public void close() throws IOException {
        positions.close();
        moves.close();
    }
}
