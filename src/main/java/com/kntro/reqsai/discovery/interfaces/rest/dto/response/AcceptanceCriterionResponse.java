package com.kntro.reqsai.discovery.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "A single acceptance criterion in Given / When / Then format")
public record AcceptanceCriterionResponse(

        @Schema(description = "Criterion unique identifier")
        UUID id,

        @Schema(description = "Parent user story id")
        UUID storyId,

        @Schema(description = "Optional scenario label. Null for AI-generated criteria.", nullable = true)
        @Nullable String scenario,

        @Schema(description = "Given — precondition or context", example = "the user is logged in")
        String given,

        @Schema(description = "When — action or trigger", example = "the user uploads a CSV file")
        String when,

        @Schema(description = "Then — expected outcome", example = "the system imports all rows and shows a success summary")
        String then,

        @Schema(description = "Timestamp when the criterion was created")
        Instant createdAt,

        @Schema(description = "Timestamp of the last update")
        Instant updatedAt
) {}
