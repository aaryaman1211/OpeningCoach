package com.openingcoach.bench;

import com.openingcoach.core.bloom.BloomFilter;
import com.openingcoach.core.cover.CoverageSolver;
import com.openingcoach.core.cover.CoverageSolver.CandidateSet;
import com.openingcoach.core.pgn.GameReplayer;
import com.openingcoach.core.pgn.PgnGame;
import com.openingcoach.core.pgn.PgnParser;
import com.openingcoach.core.pgn.ReplayedGame;
import com.openingcoach.core.reference.ReferenceIndex;
import com.openingcoach.core.select.RandomizedSelect;
import com.openingcoach.core.chess.Board;
import com.openingcoach.core.trie.OpeningTrie;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.function.LongSupplier;

/**
 * Reproducible experiments for the report. Run from the project root:
 * <pre>mvn -q -pl bench exec:java</pre>
 * Results go to bench/results/*.csv. Each timing is the median of several repetitions after
 * warm-up runs (so the JIT has compiled the code); operation counts are also reported because
 * they do not depend on the machine.
 */
public final class Benchmarks {

    private static final Path OUT = Path.of("bench/results");
    private static final int REPS = 7;

    public static void main(String[] args) throws IOException {
        Files.createDirectories(OUT);
        List<ReplayedGame> games = loadReference(Path.of("data/raw/lichess_2013-01.pgn"), 120_000);
        System.out.printf("Loaded %d games for benchmarks%n", games.size());
        trieVsScan(games);
        bloomMemoryAndFpp();
        bloomGateOnDisk(games);
        quickselectVsSort();
        coverageGreedyVsExact();
        System.out.println("Done. CSV files are in " + OUT.toAbsolutePath());
    }

    // ------------------------------------------------------------ 1. trie vs linear scan

    static void trieVsScan(List<ReplayedGame> all) throws IOException {
        try (PrintWriter csv = csv("trie_vs_scan.csv", "games,trie_build_ms,trie_nodes,trie_query_us,scan_query_us,speedup")) {
            System.out.println("\n[1] Opening trie lookup vs scanning every game (prefix of 6 plies)");
            for (int n : new int[]{1_000, 10_000, 100_000}) {
                List<ReplayedGame> games = all.subList(0, Math.min(n, all.size()));
                Random rnd = new Random(1);
                List<List<String>> queries = new ArrayList<>();
                for (int i = 0; i < 200; i++) {
                    ReplayedGame g = games.get(rnd.nextInt(games.size()));
                    queries.add(g.sanMoves().subList(0, Math.min(6, g.plies())));
                }
                OpeningTrie[] trie = new OpeningTrie[1];
                double buildMs = median(() -> {
                    long t = System.nanoTime();
                    trie[0] = new OpeningTrie(Board.startPosition().key(), 20);
                    games.forEach(trie[0]::insert);
                    return System.nanoTime() - t;
                }) / 1e6;
                double trieUs = median(() -> {
                    long t = System.nanoTime();
                    long sink = 0;
                    for (List<String> q : queries) {
                        sink += trie[0].find(q).games();
                    }
                    blackhole(sink);
                    return System.nanoTime() - t;
                }) / 1e3 / queries.size();
                double scanUs = median(() -> {
                    long t = System.nanoTime();
                    long sink = 0;
                    for (List<String> q : queries) {
                        sink += OpeningTrie.linearScan(games, q).games();
                    }
                    blackhole(sink);
                    return System.nanoTime() - t;
                }) / 1e3 / queries.size();
                csv.printf("%d,%.2f,%d,%.3f,%.3f,%.0f%n", games.size(), buildMs, trie[0].nodeCount(), trieUs, scanUs, scanUs / trieUs);
                System.out.printf("  %7d games: trie %.3f us/query, scan %.1f us/query (%.0fx), build %.1f ms%n",
                        games.size(), trieUs, scanUs, scanUs / trieUs, buildMs);
            }
        }
    }

    // ------------------------------------------------------------ 2. Bloom memory and FPP

    static void bloomMemoryAndFpp() throws IOException {
        try (PrintWriter csv = csv("bloom_memory_fpp.csv",
                "keys,target_fpp,bits_per_key,k,bloom_bytes,hashset_bytes_measured,theoretical_fpp,observed_fpp,ci95_low,ci95_high")) {
            System.out.println("\n[2] Bloom filter vs HashSet<Long>: memory and false-positive rate");
            for (int n : new int[]{10_000, 100_000, 1_000_000}) {
                for (double p : new double[]{0.01, 0.001}) {
                    SplittableRandom rnd = new SplittableRandom(n);
                    long[] keys = new long[n];
                    for (int i = 0; i < n; i++) {
                        keys[i] = rnd.nextLong();
                    }
                    BloomFilter f = BloomFilter.forExpected(n, p);
                    for (long k : keys) {
                        f.add(k);
                    }
                    long setBytes = measureHashSet(keys);
                    int trials = 1_000_000, fp = 0;
                    SplittableRandom probe = new SplittableRandom(n + 99);
                    for (int i = 0; i < trials; i++) {
                        if (f.mightContain(probe.nextLong())) {
                            fp++; // random 64-bit probes collide with a member with negligible probability
                        }
                    }
                    double obs = (double) fp / trials;
                    double se = Math.sqrt(obs * (1 - obs) / trials);
                    csv.printf("%d,%s,%.2f,%d,%d,%d,%.6f,%.6f,%.6f,%.6f%n", n, p, (double) f.bitCount() / n, f.hashCount(),
                            f.memoryBytes(), setBytes, f.expectedFpp(), obs, obs - 1.96 * se, obs + 1.96 * se);
                    System.out.printf("  n=%,9d p=%s: Bloom %,d B vs HashSet %,d B (%.0fx smaller); FPP theory %.4f%% observed %.4f%%%n",
                            n, p, f.memoryBytes(), setBytes, (double) setBytes / f.memoryBytes(), 100 * f.expectedFpp(), 100 * obs);
                }
            }
        }
    }

    /** Heap growth from building a HashSet<Long> of the keys (GC before and after; approximate but measured). */
    static long measureHashSet(long[] keys) {
        Runtime rt = Runtime.getRuntime();
        gc();
        long before = rt.totalMemory() - rt.freeMemory();
        Set<Long> set = new HashSet<>();
        for (long k : keys) {
            set.add(k);
        }
        gc();
        long after = rt.totalMemory() - rt.freeMemory();
        blackhole(set.size());
        return Math.max(0, after - before);
    }

    // ------------------------------------------------------------ 3. Bloom gate on the disk index

    static void bloomGateOnDisk(List<ReplayedGame> games) throws IOException {
        Path dir = Path.of("data/index");
        if (!ReferenceIndex.exists(dir)) {
            System.out.println("\n[3] skipped: start the server once to build data/index");
            return;
        }
        try (PrintWriter csv = csv("bloom_gate_disk.csv", "share_out_of_book,lookups,with_bloom_probes,without_bloom_probes,with_bloom_ms,without_bloom_ms");
             ReferenceIndex idx = ReferenceIndex.open(dir)) {
            System.out.println("\n[3] Reference lookups with and without the Bloom filter in front of the disk index");
            SplittableRandom rnd = new SplittableRandom(5);
            List<Long> inBook = new ArrayList<>();
            for (ReplayedGame g : games.subList(0, 5000)) {
                for (long k : g.keys()) {
                    inBook.add(k);
                }
            }
            for (double outShare : new double[]{0.1, 0.5, 0.9}) {
                long[] queries = new long[50_000];
                for (int i = 0; i < queries.length; i++) {
                    queries[i] = rnd.nextDouble() < outShare ? rnd.nextLong() : inBook.get(rnd.nextInt(inBook.size()));
                }
                long[] probes = new long[2];
                double[] ms = new double[2];
                for (int mode = 0; mode < 2; mode++) {
                    idx.setBloomEnabled(mode == 0);
                    final int m = mode;
                    ms[mode] = median(() -> {
                        idx.resetCounters();
                        long t = System.nanoTime();
                        long sink = 0;
                        for (long q : queries) {
                            sink += idx.position(q).isPresent() ? 1 : 0;
                        }
                        blackhole(sink);
                        probes[m] = idx.diskProbes();
                        return System.nanoTime() - t;
                    }) / 1e6;
                }
                idx.setBloomEnabled(true);
                csv.printf("%.1f,%d,%d,%d,%.1f,%.1f%n", outShare, queries.length, probes[0], probes[1], ms[0], ms[1]);
                System.out.printf("  %2.0f%% out of book: %,d disk probes with Bloom vs %,d without; %.0f ms vs %.0f ms%n",
                        outShare * 100, probes[0], probes[1], ms[0], ms[1]);
            }
        }
    }

    // ------------------------------------------------------------ 4. Quickselect vs sorting

    static void quickselectVsSort() throws IOException {
        try (PrintWriter csv = csv("quickselect_vs_sort.csv",
                "n,input,select_ms,sort_ms,select_comparisons,comparisons_per_n,theory_median_per_n")) {
            System.out.println("\n[4] Randomized Quickselect (median) vs full sort");
            for (int n : new int[]{10_000, 100_000, 1_000_000}) {
                for (String input : new String[]{"random", "sorted", "few-distinct"}) {
                    Random rnd = new Random(n);
                    List<Double> xs = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        xs.add(switch (input) {
                            case "sorted" -> (double) i;
                            case "few-distinct" -> (double) rnd.nextInt(5);
                            default -> rnd.nextDouble();
                        });
                    }
                    long[] cmp = new long[1];
                    double selMs = median(() -> {
                        RandomizedSelect s = new RandomizedSelect(7);
                        long t = System.nanoTime();
                        blackhole(s.select(xs, n / 2, Comparator.naturalOrder()).hashCode());
                        cmp[0] = s.comparisons();
                        return System.nanoTime() - t;
                    }) / 1e6;
                    double sortMs = median(() -> {
                        Double[] arr = xs.toArray(new Double[0]);
                        long t = System.nanoTime();
                        Arrays.sort(arr);
                        blackhole(arr[n / 2].hashCode());
                        return System.nanoTime() - t;
                    }) / 1e6;
                    double theory = 2 + 2 * Math.log(2); // ~3.39 n for the median
                    csv.printf("%d,%s,%.2f,%.2f,%d,%.2f,%.2f%n", n, input, selMs, sortMs, cmp[0], (double) cmp[0] / n, theory);
                    System.out.printf("  n=%,9d %-12s select %.1f ms (%.2f n comparisons) vs sort %.1f ms%n",
                            n, input, selMs, (double) cmp[0] / n, sortMs);
                }
            }
        }
    }

    // ------------------------------------------------------------ 5. greedy coverage vs brute force

    static void coverageGreedyVsExact() throws IOException {
        try (PrintWriter csv = csv("coverage_greedy_vs_exact.csv",
                "sets,elements,instances,greedy_ms,exact_ms,worst_budget_ratio,mean_budget_ratio,worst_cover_ratio,mean_cover_ratio")) {
            System.out.println("\n[5] Greedy coverage vs brute force (exact) on random instances");
            for (int s : new int[]{10, 14, 18, 22}) {
                int instances = s <= 18 ? 50 : 5;
                Random rnd = new Random(s);
                double worstB = 1, sumB = 0, worstC = 1, sumC = 0, gMs = 0, eMs = 0;
                for (int t = 0; t < instances; t++) {
                    int n = 30;
                    double[] w = new double[n];
                    for (int i = 0; i < n; i++) {
                        w[i] = 1 + rnd.nextInt(10);
                    }
                    List<CandidateSet> sets = new ArrayList<>();
                    for (int j = 0; j < s; j++) {
                        Set<Integer> el = new HashSet<>();
                        int size = 2 + rnd.nextInt(6);
                        while (el.size() < size) {
                            el.add(rnd.nextInt(n));
                        }
                        sets.add(new CandidateSet(j, 5 + rnd.nextInt(30), el.stream().mapToInt(Integer::intValue).toArray()));
                    }
                    double budget = 40 + rnd.nextInt(40);
                    long t0 = System.nanoTime();
                    var gb = CoverageSolver.greedyBudget(w, sets, budget);
                    var gc = CoverageSolver.greedyCover(w, sets);
                    gMs += (System.nanoTime() - t0) / 1e6;
                    t0 = System.nanoTime();
                    var eb = CoverageSolver.exactBudget(w, sets, budget);
                    var ec = CoverageSolver.exactCover(w, sets);
                    eMs += (System.nanoTime() - t0) / 1e6;
                    double rb = eb.coveredWeight() == 0 ? 1 : gb.coveredWeight() / eb.coveredWeight();
                    double rc = gc.totalCost() == 0 ? 1 : ec.totalCost() / gc.totalCost();
                    worstB = Math.min(worstB, rb);
                    sumB += rb;
                    worstC = Math.min(worstC, rc);
                    sumC += rc;
                }
                csv.printf("%d,30,%d,%.3f,%.1f,%.3f,%.3f,%.3f,%.3f%n", s, instances, gMs / instances, eMs / instances,
                        worstB, sumB / instances, worstC, sumC / instances);
                System.out.printf("  %2d sets: greedy %.3f ms vs exact %.1f ms per instance; budget ratio worst %.2f mean %.2f;"
                                + " cover (optimal/greedy cost) worst %.2f mean %.2f%n",
                        s, gMs / instances, eMs / instances, worstB, sumB / instances, worstC, sumC / instances);
            }
        }
    }

    // ------------------------------------------------------------ helpers

    static List<ReplayedGame> loadReference(Path pgn, int limit) throws IOException {
        if (!Files.exists(pgn)) {
            pgn = Path.of("data/reference/lichess-2013-01-1700plus.pgn");
        }
        List<ReplayedGame> out = new ArrayList<>();
        try (Reader r = Files.newBufferedReader(pgn, StandardCharsets.UTF_8)) {
            PgnParser.stream(r, (PgnGame g) -> {
                if (out.size() < limit) {
                    GameReplayer.Outcome o = GameReplayer.replay(g, 20);
                    if (o.ok()) {
                        out.add(o.game());
                    }
                }
            });
        }
        return out;
    }

    /** Runs the task 3 times to warm up, then REPS times, returning the median nanoseconds. */
    static double median(LongSupplier task) {
        for (int i = 0; i < 3; i++) {
            task.getAsLong();
        }
        long[] t = new long[REPS];
        for (int i = 0; i < REPS; i++) {
            t[i] = task.getAsLong();
        }
        Arrays.sort(t);
        return t[REPS / 2];
    }

    private static volatile long sinkHole;

    /** Keeps results "used" so the JIT cannot delete the benchmarked work. */
    static void blackhole(long v) {
        sinkHole += v;
    }

    static void gc() {
        for (int i = 0; i < 3; i++) {
            System.gc();
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static PrintWriter csv(String name, String header) throws IOException {
        PrintWriter w = new PrintWriter(Files.newBufferedWriter(OUT.resolve(name), StandardCharsets.UTF_8));
        w.println(header);
        return w;
    }
}
