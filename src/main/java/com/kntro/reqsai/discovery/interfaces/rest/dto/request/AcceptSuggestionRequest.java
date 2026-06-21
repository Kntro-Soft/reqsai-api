package com.kntro.reqsai.discovery.interfaces.rest.dto.request;

import com.kntro.reqsai.discovery.domain.model.Priority;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * Analyst accepts a suggestion, optionally overriding its draft fields before committing to the
 * backlog. All fields are optional — null means "use the suggestion's original draft value".
 */
@Schema(description = "Accept a suggestion, optionally editing its draft before persistence")
public record AcceptSuggestionRequest(

        @Schema(description = "Override the draft title; null keeps the original", nullable = true)
        @Nullable String editedTitle,

        @Schema(description = "Override the draft role", nullable = true)
        @Nullable String editedRole,

        @Schema(description = "Override the draft action", nullable = true)
        @Nullable String editedAction,

        @Schema(description = "Override the draft benefit", nullable = true)
        @Nullable String editedBenefit,

        @Schema(description = "Override the draft priority", nullable = true,
                allowableValues = {"LOW", "MEDIUM", "HIGH", "CRITICAL"})
        @Nullable Priority editedPriority,

        @Schema(description = "Override the draft story points", nullable = true, example = "3")
        @Nullable Integer editedStoryPoints
) {
}
