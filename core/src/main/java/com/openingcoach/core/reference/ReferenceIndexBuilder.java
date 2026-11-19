package com.openingcoach.core.reference;

import com.openingcoach.core.bloom.BloomFilter;
import com.openingcoach.core.pgn.GameReplayer;
import com.openingcoach.core.pgn.GameResult;
import com.openingcoach.core.pgn.PgnGame;
import com.openingcoach.core.pgn.PgnParser;
import com.openingcoach.core.pgn.ReplayedGame;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Builds the on-disk reference index from a PGN file of strong players' games.
 *
 * <p>Output (in {@code outDir}):
 * <ul>
 *   <li>{@code positions.bin}: one 24-byte record per position, sorted by key:
 *       key(8) whiteWins(4) draws(4) blackWins(4) pad(4)</li>
 *   <li>{@code moves.bin}: one 40-byte record per (position, move), sorted by parent key then
 *       popularity: parentKey(8) childKey(8) san(8 ASCII) whiteWins(4) draws(4) blackWins(4) pad(4)</li>
 *   <li>{@code positions.bloom}: Bloom filter of every position key, loaded into memory at startup</li>
 *   <li>{@code meta.properties}: counts and build settings</li>
 * </ul>
 * Run it from the command line:
 * {@code java -cp core.jar com.openingcoach.core.reference.ReferenceIndexBuilder in.pgn outDir [minElo] [maxPly] [fpp]}
 */
public final class ReferenceIndexBuilder {

    public static final int POSITION_RECORD = 24;
    public static final int MOVE_RECORD = 40;

    /** Summary of a build. */
    public record BuildReport(int gamesRead, int gamesUsed, int belowElo, int skipped, int positions, int moves,
                              long bloomBits, int bloomHashes, double bloomExpectedFpp) {
    }

    private record EdgeKey(long parent, String san) {
    }

    private ReferenceIndexBuilder() {
    }

    public static BuildReport build(Reader pgn, Path outDir, int minElo, int maxPly, double targetFpp) throws IOException {
        Map<Long, int[]> positions = new HashMap<>();
        Map<EdgeKey, long[]> edges = new HashMap<>(); // [childKey, w, d, b]
        int[] counters = new int[4]; // read, used, belowElo, skipped

        PgnParser.stream(pgn, (PgnGame g) -> {
            counters[0]++;
            if (g.elo(true) < minElo || g.elo(false) < minElo) {
                counters[2]++;
                return;
            }
            GameReplayer.Outcome o = GameReplayer.replay(g, maxPly);
            if (!o.ok()) {
                counters[3]++;
                return;
            }
            counters[1]++;
            ReplayedGame rg = o.game();
            int col = resultColumn(rg.result());
            positions.computeIfAbsent(rg.keys()[0], k -> new int[3])[col]++;
            for (int i = 0; i < rg.plies(); i++) {
                long parent = rg.keys()[i], child = rg.keys()[i + 1];
                positions.computeIfAbsent(child, k -> new int[3])[col]++;
                long[] e = edges.computeIfAbsent(new EdgeKey(parent, rg.sanMoves().get(i)), k -> new long[]{child, 0, 0, 0});
                e[1 + col]++;
            }
        });
        if (counters[1] == 0) {
            throw new IllegalArgumentException("No usable reference games (check the PGN file and minimum rating)");
        }

        Files.createDirectories(outDir);

        // positions: sorted by key
        long[] posKeys = positions.keySet().stream().mapToLong(Long::longValue).sorted().toArray();
        try (DataOutputStream out = data(outDir.resolve("positions.bin"))) {
            for (long k : posKeys) {
                int[] s = positions.get(k);
                out.writeLong(k);
                out.writeInt(s[0]);
                out.writeInt(s[1]);
                out.writeInt(s[2]);
                out.writeInt(0);
            }
        }

        // moves: sorted by parent key, then most games first
        List<Map.Entry<EdgeKey, long[]>> edgeList = new ArrayList<>(edges.entrySet());
        edgeList.sort((a, b) -> {
            int c = Long.compare(a.getKey().parent(), b.getKey().parent());
            if (c != 0) {
                return c;
            }
            long ga = a.getValue()[1] + a.getValue()[2] + a.getValue()[3];
            long gb = b.getValue()[1] + b.getValue()[2] + b.getValue()[3];
            c = Long.compare(gb, ga);
            return c != 0 ? c : a.getKey().san().compareTo(b.getKey().san());
        });
        try (DataOutputStream out = data(outDir.resolve("moves.bin"))) {
            for (Map.Entry<EdgeKey, long[]> e : edgeList) {
                long[] v = e.getValue();
                out.writeLong(e.getKey().parent());
                out.writeLong(v[0]);
                out.write(sanBytes(e.getKey().san()));
                out.writeInt((int) v[1]);
                out.writeInt((int) v[2]);
                out.writeInt((int) v[3]);
                out.writeInt(0);
            }
        }

        BloomFilter bloom = BloomFilter.forExpected(posKeys.length, targetFpp);
        for (long k : posKeys) {
            bloom.add(k);
        }
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(outDir.resolve("positions.bloom")))) {
            bloom.writeTo(out);
        }

        BuildReport report = new BuildReport(counters[0], counters[1], counters[2], counters[3], posKeys.length,
                edgeList.size(), bloom.bitCount(), bloom.hashCount(), bloom.expectedFpp());
        Properties meta = new Properties();
        meta.setProperty("gamesRead", String.valueOf(report.gamesRead()));
        meta.setProperty("gamesUsed", String.valueOf(report.gamesUsed()));
        meta.setProperty("belowElo", String.valueOf(report.belowElo()));
        meta.setProperty("skipped", String.valueOf(report.skipped()));
        meta.setProperty("positions", String.valueOf(report.positions()));
        meta.setProperty("moves", String.valueOf(report.moves()));
        meta.setProperty("minElo", String.valueOf(minElo));
        meta.setProperty("maxPly", String.valueOf(maxPly));
        meta.setProperty("targetFpp", String.valueOf(targetFpp));
        try (var w = Files.newBufferedWriter(outDir.resolve("meta.properties"), StandardCharsets.UTF_8)) {
            meta.store(w, "OpeningCoach reference index");
        }
        return report;
    }

    static int resultColumn(GameResult r) {
        return switch (r) {
            case WHITE_WIN -> 0;
            case DRAW -> 1;
            case BLACK_WIN -> 2;
            case UNFINISHED -> throw new IllegalArgumentException("Unfinished game");
        };
    }

    static byte[] sanBytes(String san) {
        byte[] raw = san.getBytes(StandardCharsets.US_ASCII);
        if (raw.length > 8) {
            throw new IllegalArgumentException("SAN longer than 8 characters: " + san);
        }
        byte[] out = new byte[8];
        System.arraycopy(raw, 0, out, 0, raw.length);
        return out;
    }

    private static DataOutputStream data(Path p) throws IOException {
        return new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(p), 1 << 16));
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: ReferenceIndexBuilder <games.pgn> <outDir> [minElo=1700] [maxPly=20] [fpp=0.01]");
            System.exit(2);
        }
        int minElo = args.length > 2 ? Integer.parseInt(args[2]) : 1700;
        int maxPly = args.length > 3 ? Integer.parseInt(args[3]) : 20;
        double fpp = args.length > 4 ? Double.parseDouble(args[4]) : 0.01;
        long t0 = System.nanoTime();
        try (Reader r = Files.newBufferedReader(Path.of(args[0]), StandardCharsets.UTF_8)) {
            BuildReport rep = build(r, Path.of(args[1]), minElo, maxPly, fpp);
            System.out.printf("Built reference index in %.1f s: %s%n", (System.nanoTime() - t0) / 1e9, rep);
        }
    }
}
