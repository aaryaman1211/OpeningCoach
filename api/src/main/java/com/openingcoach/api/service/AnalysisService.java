package com.openingcoach.api.service;

import com.openingcoach.api.CoachProperties;
import com.openingcoach.api.dto.Dtos.AnalysisCreated;
import com.openingcoach.api.dto.Dtos.CreateAnalysisRequest;
import com.openingcoach.api.dto.Dtos.PairRequest;
import com.openingcoach.api.dto.Dtos.StudyPlanRequest;
import com.openingcoach.api.dto.Dtos.Timed;
import com.openingcoach.api.source.GameSourceClient;
import com.openingcoach.core.analysis.Matchup;
import com.openingcoach.core.analysis.OpeningAnalyzer;
import com.openingcoach.core.analysis.Repertoire;
import com.openingcoach.core.pgn.PgnGame;
import com.openingcoach.core.pgn.PgnParser;
import com.openingcoach.core.reference.ReferenceIndex;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Application service: fetch games, build the repertoire, and run each analysis with timing. */
@Service
public class AnalysisService {

    private static final int MAX_STORED = 30;

    private final GameSourceClient sources;
    private final ReferenceService reference;
    private final CoachProperties props;
    private final Map<String, OpeningAnalyzer> store = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, OpeningAnalyzer> eldest) {
                    return size() > MAX_STORED;
                }
            });

    public AnalysisService(GameSourceClient sources, ReferenceService reference, CoachProperties props) {
        this.sources = sources;
        this.reference = reference;
        this.props = props;
    }

    public AnalysisCreated create(CreateAnalysisRequest req) {
        long t0 = System.nanoTime();
        boolean white = req.colour().equals("white");
        String player = playerName(req.source(), req.username());
        List<PgnGame> games = fetchGames(req.source(), req.username(), white, req.maxGames(), req.includeBullet(), req.pgnText());
        Repertoire rep = Repertoire.build(games, player, white, props.maxPly());
        if (rep.games().isEmpty()) {
            Repertoire.ImportReport r = rep.report();
            throw new IllegalArgumentException("None of the " + r.gamesRead() + " games could be used: "
                    + r.otherPlayerOrColour() + " were not " + player + " playing " + req.colour() + ", "
                    + r.nonStandard() + " were variants, " + r.unfinished() + " unfinished, " + r.illegal()
                    + " had illegal moves");
        }
        return store(rep, req.source(), t0);
    }

    /**
     * Downloads (or reads) the player's games once and builds both repertoires, so the user can
     * switch between their White and Black openings without importing twice.
     */
    public PairCreated createPair(PairRequest req) {
        long t0 = System.nanoTime();
        String player = playerName(req.source(), req.username());
        List<PgnGame> games = fetchGames(req.source(), req.username(), null, req.maxGames(), req.includeBullet(), req.pgnText());
        Repertoire white = Repertoire.build(games, player, true, props.maxPly());
        Repertoire black = Repertoire.build(games, player, false, props.maxPly());
        if (white.games().isEmpty() && black.games().isEmpty()) {
            Repertoire.ImportReport r = white.report();
            throw new IllegalArgumentException("None of the " + r.gamesRead() + " games could be used. Check that '"
                    + player + "' is the name used in the games.");
        }
        return new PairCreated(store(white, req.source(), t0), store(black, req.source(), t0), ms(t0));
    }

    private AnalysisCreated store(Repertoire rep, String source, long t0) {
        OpeningAnalyzer analyzer = new OpeningAnalyzer(rep, reference.book(), 20261119L);
        String id = UUID.randomUUID().toString();
        store.put(id, analyzer);
        return new AnalysisCreated(id, analyzer.summary(), ms(t0), source);
    }

    private static String playerName(String source, String username) {
        return "demo".equals(source) ? "demo_player" : username;
    }

    private List<PgnGame> fetchGames(String source, String username, Boolean white, Integer maxGames,
                                     Boolean includeBullet, String pgnText) {
        int max = Math.min(maxGames == null ? 500 : maxGames, props.maxGames());
        boolean bullet = Boolean.TRUE.equals(includeBullet);
        String pgn = switch (source) {
            case "lichess" -> sources.lichess(username, white, max, bullet);
            case "chesscom" -> sources.chessCom(username, white, max, bullet);
            case "demo" -> {
                Path demo = ReferenceService.resolveDataDir(props.dataDir()).resolve("samples/demo_player.pgn");
                try {
                    yield Files.readString(demo, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new NotFoundException("Demo games not found at " + demo + " - run scripts/make-demo-sample.sh");
                }
            }
            case "pgn" -> {
                if (pgnText == null || pgnText.isBlank()) {
                    throw new IllegalArgumentException("Upload a PGN file (pgnText is empty)");
                }
                yield pgnText;
            }
            default -> throw new IllegalArgumentException("Unknown source " + source);
        };
        List<PgnGame> games = PgnParser.parseAll(pgn);
        if (games.isEmpty()) {
            throw new IllegalArgumentException("No games found for '" + playerName(source, username) + "'");
        }
        return games;
    }

    public record PairCreated(AnalysisCreated white, AnalysisCreated black, double millis) {
    }

    public OpeningAnalyzer get(String id) {
        OpeningAnalyzer a = store.get(id);
        if (a == null) {
            throw new NotFoundException("Analysis " + id + " not found (it may have expired; import the games again)");
        }
        return a;
    }

    public Timed<OpeningAnalyzer.NodeView> node(String id, String moves) {
        OpeningAnalyzer a = get(id);
        List<String> path = moves == null || moves.isBlank() ? List.of()
                : Arrays.stream(moves.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (path.size() > 60) {
            throw new IllegalArgumentException("Move path too long (max 60 plies)");
        }
        return timed(() -> a.node(path), Map.of("name", "Trie lookup + Bloom-gated reference lookup",
                "complexity", "O(L) trie steps + O(log P) disk probes per in-book position"));
    }

    public Timed<OpeningAnalyzer.WeakLinesResult> weakLines(String id, int k, int minGames) {
        OpeningAnalyzer a = get(id);
        Timed<OpeningAnalyzer.WeakLinesResult> t = timed(() -> a.weakLines(k, minGames), Map.of());
        Map<String, Object> algo = new LinkedHashMap<>();
        algo.put("name", "Randomized Quickselect (top-k) + Randomized QuickSort (k items)");
        algo.put("complexity", "expected O(N + k log k) for N candidate lines");
        algo.put("candidates", t.result().candidates());
        algo.put("selectComparisons", t.result().selectComparisons());
        algo.put("sortComparisons", t.result().sortComparisons());
        return new Timed<>(t.result(), t.millis(), algo);
    }

    public Timed<OpeningAnalyzer.OutOfBookResult> outOfBook(String id, int k) {
        OpeningAnalyzer a = get(id);
        long probesBefore = reference.index().map(ReferenceIndex::diskProbes).orElse(0L);
        var before = reference.index().map(ReferenceIndex::metrics).orElse(null);
        Timed<OpeningAnalyzer.OutOfBookResult> t = timed(() -> a.outOfBook(k), Map.of());
        Map<String, Object> algo = new LinkedHashMap<>();
        algo.put("name", "Bloom filter gate, then exact binary search on disk");
        reference.index().ifPresent(idx -> {
            var after = idx.metrics();
            algo.put("lookups", after.lookups() - before.lookups());
            algo.put("bloomRejected", after.bloomRejections() - before.bloomRejections());
            algo.put("confirmedInBook", after.confirmedHits() - before.confirmedHits());
            algo.put("falsePositives", after.falsePositives() - before.falsePositives());
            algo.put("diskProbes", idx.diskProbes() - probesBefore);
        });
        return new Timed<>(t.result(), t.millis(), algo);
    }

    public Timed<OpeningAnalyzer.StudyPlan> studyPlan(String id, StudyPlanRequest req) {
        OpeningAnalyzer a = get(id);
        double mpp = req.minutesPerPly() == null ? 2.0 : req.minutesPerPly();
        int extra = req.extraPlies() == null ? 6 : req.extraPlies();
        int weak = req.weakLines() == null ? 15 : req.weakLines();
        int minGames = req.minGames() == null ? 3 : req.minGames();
        Timed<OpeningAnalyzer.StudyPlan> t = timed(() -> a.studyPlan(req.budgetMinutes(), mpp, extra, weak, minGames), Map.of());
        Map<String, Object> algo = new LinkedHashMap<>();
        algo.put("name", req.budgetMinutes() == null ? "Greedy weighted set cover"
                : "Budgeted maximum coverage (ratio-greedy vs best single line)");
        algo.put("approximate", true);
        algo.put("candidateLines", t.result().candidateLines());
        algo.put("bruteForceCompared", t.result().optimalValue() != null);
        return new Timed<>(t.result(), t.millis(), algo);
    }

    public Timed<OpeningAnalyzer.HighlightsResult> highlights(String id, int k, int minOpponentElo) {
        OpeningAnalyzer a = get(id);
        Timed<OpeningAnalyzer.HighlightsResult> t = timed(() -> a.highlights(k, minOpponentElo), Map.of());
        Map<String, Object> algo = new LinkedHashMap<>();
        algo.put("name", "Randomized Quickselect (k quickest wins) + Randomized QuickSort");
        algo.put("candidates", t.result().candidates());
        return new Timed<>(t.result(), t.millis(), algo);
    }

    public Timed<OpeningAnalyzer.TopLinesResult> topLines(String id, int k, int depth) {
        OpeningAnalyzer a = get(id);
        Timed<OpeningAnalyzer.TopLinesResult> t = timed(() -> a.topLines(k, depth), Map.of());
        Map<String, Object> algo = new LinkedHashMap<>();
        algo.put("name", "Trie walk + randomized Quickselect (top-k by games)");
        algo.put("candidates", t.result().candidates());
        algo.put("selectComparisons", t.result().selectComparisons());
        return new Timed<>(t.result(), t.millis(), algo);
    }

    public Timed<Matchup.MatchupResult> matchup(String yourId, String theirId, int minGames, int k) {
        OpeningAnalyzer you = get(yourId), them = get(theirId);
        Timed<Matchup.MatchupResult> t = timed(() -> Matchup.compare(you.repertoire(), them.repertoire(), minGames, k,
                20261119L), Map.of());
        Map<String, Object> algo = new LinkedHashMap<>();
        algo.put("name", "Simultaneous DFS over both tries (shared lines only) + randomized Quickselect");
        algo.put("visitedNodes", t.result().visitedNodes());
        algo.put("yourTrieNodes", you.repertoire().trie().nodeCount());
        algo.put("theirTrieNodes", them.repertoire().trie().nodeCount());
        return new Timed<>(t.result(), t.millis(), algo);
    }

    private static <T> Timed<T> timed(Supplier<T> work, Map<String, Object> algo) {
        long t0 = System.nanoTime();
        T result = work.get();
        return new Timed<>(result, ms(t0), algo);
    }

    private static double ms(long t0) {
        return Math.round((System.nanoTime() - t0) / 10_000.0) / 100.0;
    }
}
