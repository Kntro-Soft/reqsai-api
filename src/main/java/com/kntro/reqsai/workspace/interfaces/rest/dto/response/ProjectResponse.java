package com.kntro.reqsai.workspace.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Created project details")
public record ProjectResponse(
        UUID id,
        UUID organizationId,
        String name,
        String description,
        List<String> programmingLanguages,
        List<String> frameworks,
        List<String> clientPlatforms,
        List<String> databases,
        String architecture,
        String domain,
        String status,
        String avatarUrl,
        Instant createdAt,
        Instant updatedAt
) {}
