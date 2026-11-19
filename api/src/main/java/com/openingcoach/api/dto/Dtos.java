package com.openingcoach.api.dto;

import com.openingcoach.core.analysis.OpeningAnalyzer;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/** Request and response bodies of the REST API. */
public final class Dtos {

    private Dtos() {
    }

    /** POST /api/analyses */
    public record CreateAnalysisRequest(
            @NotNull @Pattern(regexp = "lichess|chesscom|pgn|demo", message = "must be lichess, chesscom, pgn or demo") String source,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{2,30}", message = "must be 2-30 letters, digits, _ or -") String username,
            @NotNull @Pattern(regexp = "white|black", message = "must be white or black") String colour,
            @Min(1) @Max(2000) Integer maxGames,
            Boolean includeBullet,
            @Size(max = 20_000_000, message = "PGN text is limited to 20 MB") String pgnText) {
    }

    /** POST /api/analyses/{id}/study-plan */
    public record StudyPlanRequest(
            @DecimalMin("0") @DecimalMax("100000") Double budgetMinutes,
            @DecimalMin("0.1") @DecimalMax("60") Double minutesPerPly,
            @Min(0) @Max(20) Integer extraPlies,
            @Min(1) @Max(40) Integer weakLines,
            @Min(1) @Max(1000) Integer minGames) {
    }

    /** Wraps any result with how long the algorithm took on the server. */
    public record Timed<T>(T result, double millis, Map<String, Object> algorithm) {
    }

    public record AnalysisCreated(String id, OpeningAnalyzer.Summary summary, double millis, String source) {
    }

    public record ReferenceInfo(boolean available, String unavailableReason, Long positions, Long moves,
                                Map<String, Object> bloom, Map<String, Object> lookups, Map<String, String> build) {
    }
}
