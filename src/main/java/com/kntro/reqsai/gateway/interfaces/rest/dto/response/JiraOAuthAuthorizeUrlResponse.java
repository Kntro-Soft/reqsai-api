package com.kntro.reqsai.gateway.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** The Atlassian authorize URL to redirect the user to, plus the signed state embedded in it. */
@Schema(description = "Jira OAuth authorize URL + signed state")
public record JiraOAuthAuthorizeUrlResponse(
        @Schema(description = "Full Atlassian authorize URL") String url,
        @Schema(description = "Signed, stateless CSRF state token embedded in the URL") String state
) {}
