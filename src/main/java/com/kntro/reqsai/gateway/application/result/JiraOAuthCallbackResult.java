package com.kntro.reqsai.gateway.application.result;

import com.kntro.reqsai.gateway.application.port.JiraOAuthPort.Site;
import com.kntro.reqsai.gateway.domain.model.IntegrationConnection;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Outcome of the Jira OAuth callback (ADR-0022): either a saved {@link #connection} (a site was chosen or
 * auto-selected), or a non-empty list of {@link #sites} to choose from (multiple sites, no {@code cloudId}
 * yet) — in which case nothing was persisted and the frontend re-POSTs with a chosen {@code cloudId}.
 * Exactly one of the two is non-null.
 */
public record JiraOAuthCallbackResult(
        @Nullable IntegrationConnection connection,
        @Nullable List<Site> sites
) {

    public static JiraOAuthCallbackResult saved(IntegrationConnection connection) {
        return new JiraOAuthCallbackResult(connection, null);
    }

    public static JiraOAuthCallbackResult needsSiteSelection(List<Site> sites) {
        return new JiraOAuthCallbackResult(null, sites);
    }

    /** True when a connection was saved; false when the caller must pick a site. */
    public boolean isSaved() {
        return connection != null;
    }
}
