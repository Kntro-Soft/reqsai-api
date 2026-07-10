package com.kntro.reqsai.gateway.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Returned by the OAuth callback (HTTP 200) when the user has access to multiple Atlassian sites and has
 * not yet chosen one. Nothing was persisted; the frontend re-POSTs the callback with a chosen
 * {@code cloudId}.
 */
@Schema(description = "Multiple accessible Jira sites to choose from (no connection saved yet)")
public record JiraOAuthSitesResponse(
        List<JiraOAuthSiteResponse> sites
) {}
