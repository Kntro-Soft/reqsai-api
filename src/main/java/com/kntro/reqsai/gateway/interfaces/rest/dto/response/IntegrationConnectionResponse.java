package com.kntro.reqsai.gateway.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Organization integration connection resource. NEVER carries the API token or any OAuth token.
 * <p>
 * {@code credentialType} is {@code "API_TOKEN"} or {@code "OAUTH2"}; {@code email} is populated only for
 * {@code API_TOKEN} connections and is {@code null} for {@code OAUTH2}.
 */
@Schema(description = "Organization integration connection (no API/OAuth token is ever returned)")
public record IntegrationConnectionResponse(
        UUID id,
        UUID organizationId,
        String provider,
        @Schema(description = "Credential type", allowableValues = {"API_TOKEN", "OAUTH2"})
        String credentialType,
        String siteUrl,
        @Schema(description = "Jira account email; null for OAUTH2 connections")
        @Nullable String email,
        String status,
        @Nullable Instant lastVerifiedAt,
        Instant createdAt,
        Instant updatedAt
) {}
