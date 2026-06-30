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
        Instant createdAt,
        Instant updatedAt
) {}
