package com.kntro.reqsai.overview.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

@Schema(description = "Dashboard of one project; a section is null when the caller may not read it")
public record ProjectOverviewResponse(Instant generatedAt,
        @Schema(description = "Weeks the weekly series cover", example = "12") int weeks,
        @Schema(description = "Requires STORY_READ", nullable = true) @Nullable BacklogOverviewResponse backlog,
        @Schema(description = "Requires SESSION_READ", nullable = true) @Nullable DiscoveryOverviewResponse discovery,
        @Schema(description = "Each figure requires its own read permission", nullable = true) @Nullable KnowledgeOverviewResponse knowledge,
        @Schema(description = "Requires INTEGRATION_READ", nullable = true) @Nullable CodeOverviewResponse code) {
}
