package com.kntro.reqsai.gateway.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/** Organization integration connection resource. NEVER carries the API token. */
@Schema(description = "Organization integration connection (the API token is never returned)")
public record IntegrationConnectionResponse(
        UUID id,
        UUID organizationId,
        String provider,
        String siteUrl,
        String email,
        String status,
        @Nullable Instant lastVerifiedAt,
        Instant createdAt,
        Instant updatedAt
) {}
