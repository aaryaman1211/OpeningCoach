package com.openingcoach.api.web;

import com.openingcoach.api.dto.Dtos.AnalysisCreated;
import com.openingcoach.api.dto.Dtos.CreateAnalysisRequest;
import com.openingcoach.api.dto.Dtos.StudyPlanRequest;
import com.openingcoach.api.dto.Dtos.Timed;
import com.openingcoach.api.service.AnalysisService;
import com.openingcoach.core.analysis.Matchup;
import com.openingcoach.core.analysis.OpeningAnalyzer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analyses")
public class AnalysisController {

    private final AnalysisService service;

    public AnalysisController(AnalysisService service) {
        this.service = service;
    }

    /** Imports a player's games (Lichess, Chess.com or uploaded PGN) and builds their opening trie. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AnalysisCreated create(@Valid @RequestBody CreateAnalysisRequest req) {
        return service.create(req);
    }

    @GetMapping("/{id}")
    public OpeningAnalyzer.Summary summary(@PathVariable String id) {
        return service.get(id).summary();
    }

    /** Explorer: the position after a comma-separated SAN path, e.g. ?moves=e4,c5,Nf3 */
    @GetMapping("/{id}/tree")
    public Timed<OpeningAnalyzer.NodeView> tree(@PathVariable String id,
                                                @RequestParam(defaultValue = "") String moves) {
        return service.node(id, moves);
    }

    @GetMapping("/{id}/weak-lines")
    public Timed<OpeningAnalyzer.WeakLinesResult> weakLines(@PathVariable String id,
                                                            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int k,
                                                            @RequestParam(defaultValue = "3") @Min(1) @Max(1000) int minGames) {
        return service.weakLines(id, k, minGames);
    }

    @GetMapping("/{id}/out-of-book")
    public Timed<OpeningAnalyzer.OutOfBookResult> outOfBook(@PathVariable String id,
                                                            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int k) {
        return service.outOfBook(id, k);
    }

    /** The player's most-played lines at a given depth (their repertoire). */
    @GetMapping("/{id}/top-lines")
    public Timed<OpeningAnalyzer.TopLinesResult> topLines(@PathVariable String id,
                                                          @RequestParam(defaultValue = "10") @Min(1) @Max(50) int k,
                                                          @RequestParam(defaultValue = "6") @Min(1) @Max(20) int depth) {
        return service.topLines(id, k, depth);
    }

    /** Opponent prep: your analysis {id} against the opponent's analysis {opponentId} (opposite colours). */
    @GetMapping("/{id}/matchup/{opponentId}")
    public Timed<Matchup.MatchupResult> matchup(@PathVariable String id, @PathVariable String opponentId,
                                                @RequestParam(defaultValue = "2") @Min(1) @Max(1000) int minGames,
                                                @RequestParam(defaultValue = "8") @Min(1) @Max(30) int k) {
        return service.matchup(id, opponentId, minGames, k);
    }

    @PostMapping("/{id}/study-plan")
    public Timed<OpeningAnalyzer.StudyPlan> studyPlan(@PathVariable String id, @Valid @RequestBody StudyPlanRequest req) {
        return service.studyPlan(id, req);
    }
}
