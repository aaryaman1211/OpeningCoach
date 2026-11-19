# OpeningCoach

A personal chess opening coach. Enter your Lichess or Chess.com username (or upload a PGN file).
OpeningCoach replays your finished games and compares your first 10 moves with 21,925 games between
players rated 1700+. It then shows:

- **Weak lines**: where you lose the most points compared with strong players from the same position.
- **Leaving theory**: the move where your games leave known theory, and what strong players play there instead.
- **Study plan**: which lines to study in the time you have ("Fit my time") or to fix everything ("Fix everything").
- **One import, both colours**: your White and Black games are analysed together; the White/Black switch in the
  header flips every screen (and the opponent's colour) without importing again.
- **Board**: an animated, interactive board, shown as a 3D table-top view (or flat). Drag or click pieces to play any legal move, see arrows for strong
  players' choices and your favourite move, draw your own arrows with right-drag, step with the arrow keys, and
  auto-play your main line. A gauge beside the board shows your score next to strong players'.
- **Scout opponent**: load an opponent's games to see their repertoire, where they struggle, your best weapons and
  danger zones against them, and an animated prediction of how your game will start.
- **The greats**: ten great players. Famous games (Carlsen, Kasparov, Fischer, Morphy, Anderssen, Réti, Ed. Lasker)
  replay on the 3D board with a jump to the decisive move; current stars (Nakamura, Gukesh, Caruana) load live from
  Chess.com with their best recent wins and opening trees.
- **Under the hood**: Bloom filter parameters, live lookup counters and server timings.

CIA 3 Advanced Algorithm project. All assessed algorithms are hand-written in Java in `core/` (no Spring imports there).

## Algorithms

| Technique | Syllabus | Where | Role in the app | Complexity |
|---|---|---|---|---|
| Trie | Unit 4 | `core/.../trie/OpeningTrie.java` | Your games as a tree of moves; every node stores W/D/L | insert O(L), lookup O(L) vs O(G·L) scan |
| Bloom filter | Unit 6, CO5 | `core/.../bloom/BloomFilter.java` | In-memory "did strong players ever reach this position?" check in front of the on-disk index; every "maybe" is confirmed by binary search | O(k) per query; m = −n ln p/(ln 2)², k = (m/n) ln 2 |
| Randomized Quickselect | Unit 6, CO5 | `core/.../select/RandomizedSelect.java` | Picks the k weakest lines / most common theory exits | expected O(n), about 3.39n comparisons for the median |
| Randomized QuickSort | Unit 6, CO5 | `core/.../select/RandomizedQuickSort.java` | Orders the k items shown | expected O(k log k) |
| Trie intersection (opponent matchup) | Unit 4 | `core/.../analysis/Matchup.java` | Simultaneous DFS over your trie and the opponent's, visiting only shared lines | O(S) for S shared lines |
| Set cover / budgeted max coverage | Unit 5 (approximation), Unit 6 (NP-hardness) | `core/.../cover/CoverageSolver.java` | Chooses study lines | greedy: H(d) ≤ 1 + ln d for cover; ≥ (1 − 1/e)/2 for budget. Brute force O(2^s·s) for s ≤ 22 checks the real ratio |

Infrastructure (not assessed): `chess/Board.java` replays SAN moves legally, generates legal moves (verified with
standard perft counts), and computes Zobrist position keys so transpositions are recognised. `pgn/` reads PGN. `reference/` builds and reads the sorted on-disk index.

Approximation results are labelled approximate in the API and UI; the study plan never claims to be optimal.

## Run it

Prerequisites: Java 21+, Maven 3.9+, Node 18+, plus curl and zstd only if you rebuild the data.

```bash
./run.sh            # builds whatever changed, then starts everything on http://localhost:8080
./run.sh --rebuild  # force a full rebuild first
```

Open http://localhost:8080 and choose **Demo game** to try it offline, or enter a Lichess / Chess.com username.
The server keeps analyses in memory. If it restarts while a page is open, the page waits for it and reloads your
games by itself.

Manual steps, if you prefer:

```bash
cd frontend && npm install && npm run build && cd ..   # writes the UI into api/src/main/resources/static
mvn install                                           # builds and runs the 73 tests
java -jar api/target/openingcoach-api-1.0.0.jar
```

Frontend work with hot reload: keep the Java server running, then `cd frontend && npm run dev` and open
http://localhost:5173. The dev page needs the Java server on port 8080.

### Data

| File | Source | Licence |
|---|---|---|
| `data/reference/lichess-2013-01-1700plus.pgn` | Lichess open database, January 2013, both players rated ≥ 1700 (21,925 games) | CC0 |
| Titillium Web font (loaded from Google Fonts) | Accademia di Belle Arti di Urbino | SIL Open Font License |
| `frontend/public/pieces/*.svg` | Chess pieces by Colin M.L. Burnett, via Wikimedia Commons | CC BY-SA 3.0 |
| `data/samples/demo_player.pgn` | 900 club-level games (1200–1650) from the same month, one side relabelled `demo_player` | CC0 |

Rebuild them with `scripts/fetch-reference.sh [month] [minElo]` and `scripts/make-demo-sample.sh`.
Note that the demo file mixes many real players' games under one name. It is a test fixture, not one person's record.

## API

| Method | Path | Body / params |
|---|---|---|
| POST | `/api/analyses` | `{"source":"lichess"\|"chesscom"\|"pgn"\|"demo","username":"...","colour":"white"\|"black","maxGames":500,"includeBullet":false,"pgnText":"..."}` |
| GET | `/api/analyses/{id}` | summary |
| GET | `/api/analyses/{id}/tree?moves=e4,c5,Nf3` | explorer node |
| GET | `/api/analyses/{id}/weak-lines?k=10&minGames=3` | |
| GET | `/api/analyses/{id}/out-of-book?k=10` | |
| POST | `/api/analyses/{id}/study-plan` | `{"budgetMinutes":60,"minutesPerPly":2,"extraPlies":6,"weakLines":15,"minGames":3}` (`budgetMinutes: null` = fix everything) |
| POST | `/api/analyses/pair` | `{"source","username","maxGames","includeBullet","pgnText"}`: one download, returns `{white, black}` analyses |
| GET | `/api/analyses/{id}/highlights?k=6&minOpponentElo=2400` | best wins: checkmates first, then strongest opponents (Quickselect top-k) |
| GET | `/api/analyses/{id}/top-lines?k=8&depth=6` | most-played lines at a depth |
| GET | `/api/analyses/{id}/matchup/{opponentId}?minGames=2&k=6` | weapons, dangers, predicted game (opposite colours) |
| GET | `/api/board?moves=e4,e5,Nf3` | replays a line: from/to squares and position for each ply |
| GET | `/api/reference` | Bloom filter and index statistics |

Errors are RFC 9457 `application/problem+json` with a readable `detail`. Examples:
- unknown user → 404
- invalid colour → 400
- illegal move path → 400
- Lichess rate limit → 503

```bash
curl -s -X POST localhost:8080/api/analyses -H 'Content-Type: application/json' \
  -d '{"source":"demo","username":"demo_player","colour":"white"}'
```

## Tests

`mvn test` runs 73 tests:

| Class | Tests | Highlights |
|---|---|---|
| `LegalMovesTest` | 7 | perft from the start (8,902 at depth 3), Kiwipete, positions 3 and 4 (castling, en passant, pins, promotion), SAN disambiguation, mate/stalemate |
| `MatchupTest` | 5 | best weapon and danger lines, only shared lines visited, predicted game, colour validation |
| `BoardTest` | 12 | castling, en passant, promotion, pinned-piece disambiguation, illegal moves, transposition keys |
| `PgnParserTest` | 6 | comments, nested variations, Chess960 rejected, illegal move reported with ply |
| `OpeningTrieTest` | 7 | counts, maxPly boundary, bad input, 500 random prefixes checked against a brute-force scan |
| `BloomFilterTest` | 7 | no false negatives on 100k keys, measured FPP within 5σ of theory, sizing formulas, saturation boundary, serialisation |
| `RandomizedSelectTest` | 7 | every rank vs sorting on 200 random arrays with duplicates, all-equal input, sorted input stays ~linear, comparisons ≈ 2n ln n |
| `CoverageSolverTest` | 8 | a case where greedy is suboptimal (within H(d)), the best-single-set rescue, 300 random instances vs brute force respecting both bounds, uncoverable elements |
| `ReferenceIndexTest` | 5 | disk lookups equal brute-force counts, Bloom gate never changes answers, observed FPP |
| `OpeningAnalyzerTest` | 7 | the weak line found, theory exit and alternative, explorer merge, study plan budget, empty player |
| `AnalysisApiTest` | 2 | full workflow through HTTP, validation and error responses |

The chess replay was also stress-tested on all 121,114 full-length games of the January 2013 database with zero failures.

## Benchmarks

```bash
mvn -q -pl bench exec:java        # writes bench/results/*.csv
```

Each timing is the median of 7 runs after 3 warm-up runs; operation counts are reported too.
Results on an Apple-silicon laptop:

| Experiment | Result |
|---|---|
| Trie vs scanning every game (6-move prefix) | 0.36 µs vs 12.6 ms per query at 100k games (35,000×); the gap grows with the number of games |
| Bloom vs `HashSet<Long>` memory | 1.2 MB vs 58 MB for 1M keys at p = 1% (49× smaller) |
| Bloom false-positive rate | theory 1.004% vs observed 1.002% (1M keys); theory 0.100% vs observed 0.107% |
| Bloom gate in front of the disk index | 90% out-of-book queries: 53k disk probes vs 944k without the filter, 49 ms vs 826 ms |
| Quickselect (median) vs sorting, 1M random doubles | 126 ms vs 543 ms; 3.86n comparisons (theory ≈ 3.39n) |
| Greedy coverage vs brute force (30 elements, 10–22 sets) | budget mode reached on average 97–98% of optimal, worst 81%; brute force cost grows as 2^s |

An honest negative result: on already-sorted input, Java's `Arrays.sort` (TimSort) detects the run and is faster than Quickselect.

## Limitations

- The reference is January 2013 Lichess games rated 1700+, which is club strength, not master theory.
  A later month gives a stronger reference: `scripts/fetch-reference.sh 2024-01 2200`. Those files are tens of GB.
- Only the first 10 moves (20 plies) are analysed. Chess960 and games from custom positions are skipped and counted.
- The en passant part of the position key ignores pins, a rare case that can split one position into two keys.
- Study time is estimated as plies × minutes per move, an assumption you can change in the UI.
- Analyses are kept in memory (last 30) and are lost on restart.
- Lichess may rate-limit large downloads; the app reports this as 503.

## Repository layout

```
core/      algorithms + chess/PGN infrastructure, JUnit tests (no Spring)
api/       Spring Boot REST API, validation, error handling; serves the built frontend
bench/     benchmark harness -> bench/results/*.csv
frontend/  React + Vite UI
data/      reference PGN, demo sample (index/ and raw/ are generated, not committed)
scripts/   data download and preparation
```

This folder is named `OpeningCoach.nosync` so iCloud Drive does not sync it; an `OpeningCoach` shortcut points here.
Use Git/GitHub as the backup.
