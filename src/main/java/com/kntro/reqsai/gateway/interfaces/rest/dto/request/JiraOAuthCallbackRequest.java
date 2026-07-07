package com.kntro.reqsai.gateway.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

/**
 * Request body for the Jira OAuth 2.0 (3LO) callback. {@code code} + {@code state} come from the
 * Atlassian redirect; {@code cloudId} is optional and only supplied on the second POST when the user has
 * chosen among multiple accessible sites.
 */
@Schema(description = "Jira OAuth callback: authorization code + signed state, optional chosen site")
public record JiraOAuthCallbackRequest(
        @Schema(description = "Authorization code from the Atlassian redirect")
        @NotBlank String code,

        @Schema(description = "Signed state token issued by the authorize-url endpoint")
        @NotBlank String state,

        @Schema(description = "Chosen Atlassian cloud id (only when selecting among multiple sites)")
        @Nullable String cloudId
) {}
