package com.kntro.reqsai.overview.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(description = "Dashboard of an organization over the projects the caller may see")
public record OrganizationOverviewResponse(Instant generatedAt,
        @Schema(description = "Weeks the weekly series cover", example = "12") int weeks,
        @Schema(description = "Active projects the caller may see") int projects,
        @Schema(description = "People of the organization; null unless the caller is an owner or admin", nullable = true) @Nullable MembersResponse members,
        BacklogOverviewResponse backlog, DiscoveryOverviewResponse discovery,
        @Schema(description = "Busiest projects, most active first") List<TopProjectResponse> topProjects,
        CodeOverviewResponse code) {

    @Schema(description = "Headcount of the organization")
    public record MembersResponse(@Schema(description = "Owner plus active members") long active,
            @Schema(description = "Invited members who have not joined yet") long pending,
            @Schema(description = "Active people per role (OWNER, ADMIN, MEMBER)") Map<String, Long> byRole) {
    }

    @Schema(description = "A busy project")
    public record TopProjectResponse(UUID projectId, String name,
            @Schema(description = "Whether it is the organization's sample project") boolean demo, long stories,
            long sessions,
            @Schema(description = "When the newest story or session was created", nullable = true) @Nullable Instant lastActivityAt) {
    }
}
