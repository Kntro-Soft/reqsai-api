package com.kntro.reqsai.overview.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Quality indicators of one project over a period, to validate the pilots' success criteria. "
        + "An indicator that cannot be computed for the period is null, never zero.")
public record QualityMetricsResponse(
        @Schema(description = "First day of the period (UTC, inclusive)", example = "2026-10-20") LocalDate from,
        @Schema(description = "Last day of the period (UTC, inclusive)", example = "2026-11-18") LocalDate to,
        @Schema(description = "False when no meeting ended and no story was decided in the period: the page shows a no-data message") boolean hasData,
        @Schema(description = "Meetings that ended in the period", example = "6") long sessions,
        Acceptance acceptance, PostProcessing postProcessing, Clarification clarification) {

    @Schema(description = "Stories accepted without major rewriting: accepted AI stories whose final text is at least "
            + "`similarityThreshold` similar to the text the AI proposed, over the AI stories decided in the period")
    public record Acceptance(
            @Schema(description = "Minimum text similarity (0 to 1) of an accepted story to count as not rewritten", example = "0.8") double similarityThreshold,
            @Schema(description = "AI-generated stories approved, exported or rejected in the period", example = "10") long decided,
            @Schema(description = "Of them, approved or exported", example = "8") long accepted,
            @Schema(description = "Of the accepted, kept at or above the similarity threshold", example = "6") long acceptedAsGenerated,
            @Schema(description = "Of the accepted, rewritten below the threshold", example = "2") long acceptedRewritten,
            @Schema(description = "Of the decided, rejected", example = "2") long rejected,
            @Schema(description = "Stories decided in the period that did not come from the AI, or predate the record of its text; excluded from the figures", example = "1") long withoutBaseline,
            @Schema(description = "acceptedAsGenerated over decided, between 0 and 1; null while no AI story was decided", example = "0.6", nullable = true) @Nullable Double rate) {
    }

    @Schema(description = "Time from the end of a meeting to the last review decision on the stories it produced")
    public record PostProcessing(
            @Schema(description = "Meetings of the period whose review is finished", example = "4") long measured,
            @Schema(description = "Meetings of the period with no finished review (stories still in draft, no story or no recorded decision time)", example = "2") long unmeasured,
            @Schema(description = "Mean seconds over the measured meetings; null when there are none", example = "5400", nullable = true) @Nullable Long averageSeconds,
            @Schema(description = "Median seconds over the measured meetings; null when there are none", example = "3600", nullable = true) @Nullable Long medianSeconds,
            @Schema(description = "The measured meetings, newest first") List<SessionReview> sessions) {
    }

    @Schema(description = "A meeting whose review is finished")
    public record SessionReview(
            @Schema(description = "Session identifier", example = "019756a0-1234-7abc-8def-000000000001") UUID sessionId,
            @Schema(description = "Session title", example = "Kickoff con el restaurante") String title,
            @Schema(description = "DISCOVERY or CLARIFICATION", example = "DISCOVERY", allowableValues = {"DISCOVERY", "CLARIFICATION"}) String purpose,
            @Schema(description = "When the meeting ended", example = "2026-11-10T15:30:00Z") Instant endedAt,
            @Schema(description = "Seconds from the end of the meeting to the last decision on its stories; never negative", example = "3600") long seconds) {
    }

    @Schema(description = "Follow-up meetings held to clarify requirements")
    public record Clarification(
            @Schema(description = "Meetings of the period created as clarification meetings", example = "2") long meetings,
            @Schema(description = "Every meeting of the period", example = "6") long sessions,
            @Schema(description = "meetings over sessions, between 0 and 1; null when there was no meeting", example = "0.33", nullable = true) @Nullable Double share) {
    }
}
