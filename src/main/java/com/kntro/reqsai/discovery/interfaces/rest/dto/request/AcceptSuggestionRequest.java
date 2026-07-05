package com.kntro.reqsai.discovery.interfaces.rest.dto.request;

import com.kntro.reqsai.discovery.domain.model.Priority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Analyst accepts a suggestion, optionally editing the whole draft before it is committed to the
 * backlog. All fields are optional — null (or absent) means "use the suggestion's original draft
 * value". Omit the body (or pass {@code {}}) to accept the draft as-is.
 *
 * <p>What each field affects depends on the suggestion type (see
 * {@code SessionSuggestionController#accept}); {@code editedAcceptanceCriteria}, when present,
 * REPLACES the draft criteria (all entries for NEW_STORY; only the first for EDGE_CASE).
 */
@Schema(description = "Accept a suggestion, optionally editing its whole draft before persistence")
public record AcceptSuggestionRequest(

        @Schema(description = "Override the draft title; null keeps the original", maxLength = 200, nullable = true)
        @Size(max = 200)
        @Nullable String editedTitle,

        @Schema(description = "Override the draft role", maxLength = 500, nullable = true)
        @Size(max = 500)
        @Nullable String editedRole,

        @Schema(description = "Override the draft action", maxLength = 500, nullable = true)
        @Size(max = 500)
        @Nullable String editedAction,

        @Schema(description = "Override the draft benefit", maxLength = 500, nullable = true)
        @Size(max = 500)
        @Nullable String editedBenefit,

        @Schema(description = "Override the draft priority", nullable = true,
                allowableValues = {"LOW", "MEDIUM", "HIGH", "CRITICAL"})
        @Nullable Priority editedPriority,

        @Schema(description = "Override the draft story points", nullable = true, example = "3")
        @PositiveOrZero
        @Nullable Integer editedStoryPoints,

        @Schema(description = """
                Replace the draft acceptance criteria. When present, the full list is used for \
                NEW_STORY; for EDGE_CASE only the first entry is used as the criterion added to the \
                target story. Each entry requires given/when/then. Omit (null) to keep the draft \
                criteria.""", nullable = true)
        @Valid
        @Nullable List<EditedCriterion> editedAcceptanceCriteria
) {

    /** An edited Given/When/Then acceptance criterion. {@code scenario} is an optional label. */
    @Schema(description = "An edited Given/When/Then acceptance criterion")
    public record EditedCriterion(

            @Schema(description = "Optional scenario label", maxLength = 200, nullable = true)
            @Size(max = 200)
            @Nullable String scenario,

            @Schema(description = "Given precondition", maxLength = 1000,
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 1000)
            String given,

            @Schema(description = "When action", maxLength = 1000,
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 1000)
            String when,

            @Schema(description = "Then outcome", maxLength = 1000,
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 1000)
            String then
    ) {}
}
