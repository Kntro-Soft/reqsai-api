package com.kntro.reqsai.gateway.application.command;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Completes the Jira OAuth 2.0 (3LO) flow at the organization level (ADR-0023): validates {@code state},
 * exchanges {@code code}, discovers accessible sites and — if a site is chosen ({@code cloudId} given or
 * exactly one available) — persists an OAUTH2 connection. When multiple sites exist and {@code cloudId}
 * is null the handler returns the site list WITHOUT saving.
 */
public record JiraOAuthCallbackCommand(
        UUID organizationId,
        String code,
        String state,
        @Nullable String cloudId,
        UUID requestedBy
) {}
