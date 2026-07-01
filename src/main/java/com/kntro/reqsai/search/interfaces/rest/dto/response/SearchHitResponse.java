package com.kntro.reqsai.search.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * A single global-search result for the command palette. {@code subtitle} and {@code projectId} are
 * present only when meaningful for the hit type (e.g. member email as subtitle; owning project id for
 * projects and user stories).
 */
@Schema(description = "A single global-search result")
public record SearchHitResponse(
        @Schema(description = "Kind of entity matched", example = "PROJECT",
                allowableValues = {"PROJECT", "USER_STORY", "ORGANIZATION", "MEMBER"})
        String type,
        @Schema(description = "Entity id") UUID id,
        @Schema(description = "Primary label", example = "Checkout redesign") String title,
        @Schema(description = "Secondary label (member email, org slug, ...)", nullable = true)
        @Nullable String subtitle,
        @Schema(description = "Owning project id for project-scoped hits", nullable = true)
        @Nullable UUID projectId
) {}
