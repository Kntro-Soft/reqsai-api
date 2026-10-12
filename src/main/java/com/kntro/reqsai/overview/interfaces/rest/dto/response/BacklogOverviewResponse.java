package com.kntro.reqsai.overview.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "The backlog: user stories by status and priority, estimation and coverage")
public record BacklogOverviewResponse(@Schema(description = "Stories of any status") long total,
        @Schema(description = "Stories per status (DRAFT, APPROVED, REJECTED, MERGED, EXPORTED), all present") Map<String, Long> byStatus,
        @Schema(description = "Stories per priority (LOW, MEDIUM, HIGH, CRITICAL), all present") Map<String, Long> byPriority,
        StoryPointsResponse storyPoints, CoverageResponse coverage,
        @Schema(description = "Stories created per week, oldest first, every week of the window present") List<WeekCountResponse> createdByWeek,
        @Schema(description = "Newest stories; empty on the organization overview") List<StorySummaryResponse> recent) {

    @Schema(description = "Estimation of the live backlog (draft, approved or exported stories)")
    public record StoryPointsResponse(@Schema(description = "Points of the live stories") long total,
            @Schema(description = "Points of the approved or exported stories") long approved,
            @Schema(description = "Live stories with no points yet") long unestimated) {
    }

    @Schema(description = "How well the live stories are backed up")
    public record CoverageResponse(@Schema(description = "Stories still counting as work") long live,
            @Schema(description = "Of them, stories with at least one acceptance criterion") long withAcceptanceCriteria,
            @Schema(description = "Of them, stories quoting the conversation they came from") long withOrigin,
            @Schema(description = "Of them, stories tied to a module of the client's code") long linkedToCode) {
    }
}
