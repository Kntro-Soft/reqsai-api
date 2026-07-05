package com.kntro.reqsai.discovery.interfaces.rest.dto.response;

import com.kntro.reqsai.discovery.domain.model.SuggestionStatus;
import com.kntro.reqsai.discovery.domain.model.SuggestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "AI-generated suggestion pending analyst review")
public record SuggestionResponse(

        @Schema(description = "Suggestion unique identifier")
        UUID id,

        @Schema(description = "Discovery session that produced this suggestion")
        UUID sessionId,

        @Schema(description = "Project the suggestion belongs to")
        UUID projectId,

        @Schema(description = "Suggestion classification",
                allowableValues = {"NEW_STORY", "UPDATE_STORY", "EDGE_CASE", "CLARIFYING_QUESTION"})
        SuggestionType type,

        @Schema(description = "Review status",
                allowableValues = {"PENDING", "ACCEPTED", "DISMISSED"})
        SuggestionStatus status,

        @Schema(description = "Draft title (null for CLARIFYING_QUESTION)", nullable = true)
        @Nullable String draftTitle,

        @Schema(description = "Draft role (null for CLARIFYING_QUESTION)", nullable = true)
        @Nullable String draftRole,

        @Schema(description = "Draft action (null for CLARIFYING_QUESTION)", nullable = true)
        @Nullable String draftAction,

        @Schema(description = "Draft benefit (null for CLARIFYING_QUESTION)", nullable = true)
        @Nullable String draftBenefit,

        @Schema(description = "Draft priority (null for CLARIFYING_QUESTION)", nullable = true)
        @Nullable String draftPriority,

        @Schema(description = "Draft story points (null if not estimated)", nullable = true)
        @Nullable Integer draftStoryPoints,

        @Schema(description = "Topic hint for edge-case → target-story resolution", nullable = true)
        @Nullable String relatedTopic,

        @Schema(description = "Target story for UPDATE_STORY / EDGE_CASE (null if resolution failed)", nullable = true)
        @Nullable UUID targetStoryId,

        @Schema(description = "Clarifying question text (only for CLARIFYING_QUESTION)", nullable = true)
        @Nullable String question,

        @Schema(description = "Proposed acceptance criteria for a NEW_STORY draft (empty for other types)")
        List<DraftCriterionResponse> draftAcceptanceCriteria,

        @Schema(description = "Story created or modified on acceptance; null if not yet accepted or type is CLARIFYING_QUESTION", nullable = true)
        @Nullable UUID resolvedStoryId,

        @Schema(description = "Similarity (0..1) to the target story when this is a duplicate alert", nullable = true)
        @Nullable Double similarity,

        @Schema(description = "When the suggestion was created")
        Instant createdAt,

        @Schema(description = "When the suggestion was last updated")
        Instant updatedAt
) {

    @Schema(description = "A proposed Given/When/Then acceptance criterion on a NEW_STORY draft")
    public record DraftCriterionResponse(
            @Schema(description = "Optional scenario label", nullable = true) @Nullable String scenario,
            @Schema(description = "Given precondition") String given,
            @Schema(description = "When action") String when,
            @Schema(description = "Then outcome") String then
    ) {}
}
