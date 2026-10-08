package com.kntro.reqsai.discovery.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

@Schema(description = "A story as the client sees it through a share link")
public record SharedStoryResponse(

        @Schema(description = "Story identifier")
        UUID id,

        @Schema(description = "Short title")
        String title,

        @Schema(description = "As a ...")
        String role,

        @Schema(description = "I want ...")
        String action,

        @Schema(description = "So that ...")
        String benefit,

        @Schema(description = "Priority", allowableValues = {"HIGH", "MEDIUM", "LOW"})
        String priority,

        @Schema(description = "Estimate in story points", nullable = true)
        @Nullable Integer storyPoints,

        @Schema(description = "The team's review status", allowableValues = {"DRAFT", "APPROVED", "EXPORTED"})
        String status,

        @Schema(description = "Acceptance criteria")
        List<AcceptanceCriterionResponse> acceptanceCriteria,

        @Schema(description = "Approvals and comments clients already left, oldest first")
        List<StoryFeedbackResponse> feedback
) {
}
