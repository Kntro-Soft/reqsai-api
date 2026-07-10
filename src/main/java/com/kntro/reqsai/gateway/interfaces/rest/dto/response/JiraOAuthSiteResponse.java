package com.kntro.reqsai.gateway.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** One accessible Atlassian site offered for selection during the OAuth callback. */
@Schema(description = "An accessible Atlassian Jira site")
public record JiraOAuthSiteResponse(
        @Schema(description = "Atlassian cloud id") String cloudId,
        @Schema(description = "Site base URL", example = "https://acme.atlassian.net") String url,
        @Schema(description = "Site display name") String name
) {}
