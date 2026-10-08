package com.kntro.reqsai.workspace.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Created project details")
public record ProjectResponse(
        UUID id,
        UUID organizationId,
        String name,
        @Nullable String description,
        List<String> programmingLanguages,
        List<String> frameworks,
        List<String> clientPlatforms,
        List<String> databases,
        @Nullable String architecture,
        @Nullable String domain,
        String status,
        String avatarUrl,
        Instant createdAt,
        Instant updatedAt,
        @Schema(description = "Whether this is the organization's demo project (sample content, restorable, "
                + "not counted against the plan's project limit)")
        boolean demo
) {}
