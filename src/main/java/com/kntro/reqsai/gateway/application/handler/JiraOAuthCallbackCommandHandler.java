package com.kntro.reqsai.gateway.application.handler;

import com.kntro.reqsai.gateway.application.command.JiraOAuthCallbackCommand;
import com.kntro.reqsai.gateway.application.port.IntegrationConnectionRepository;
import com.kntro.reqsai.gateway.application.port.JiraOAuthPort;
import com.kntro.reqsai.gateway.application.port.JiraOAuthPort.OAuthTokens;
import com.kntro.reqsai.gateway.application.port.JiraOAuthPort.Site;
import com.kntro.reqsai.gateway.application.result.JiraOAuthCallbackResult;
import com.kntro.reqsai.gateway.application.service.JiraOAuthStateService;
import com.kntro.reqsai.gateway.domain.exception.IntegrationsExceptions;
import com.kntro.reqsai.gateway.domain.model.ConnectionStatus;
import com.kntro.reqsai.gateway.domain.model.IntegrationConnection;
import com.kntro.reqsai.gateway.domain.model.IntegrationProviderType;
import com.kntro.reqsai.gateway.application.config.JiraOAuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Completes the Jira OAuth 2.0 (3LO) org-level flow (ADR-0022):
 * <ol>
 *   <li>reject if OAuth is not configured ({@code JIRA_OAUTH_NOT_CONFIGURED});</li>
 *   <li>validate the signed {@code state} against this org+user ({@code JIRA_OAUTH_STATE_INVALID});</li>
 *   <li>exchange the authorization {@code code} for tokens and discover accessible sites;</li>
 *   <li>if a {@code cloudId} is given use it, else if exactly one site auto-select it, else return the
 *       site list WITHOUT saving (the frontend re-POSTs with a chosen {@code cloudId});</li>
 *   <li>on selection, enforce one active connection per org ({@code INTEGRATION_ALREADY_CONNECTED}) and
 *       persist an encrypted OAUTH2 connection.</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class JiraOAuthCallbackCommandHandler {

    private final JiraOAuthProperties props;
    private final JiraOAuthStateService stateService;
    private final JiraOAuthPort oauth;
    private final IntegrationConnectionRepository connections;

    @Transactional
    public JiraOAuthCallbackResult handle(JiraOAuthCallbackCommand command) {
        if (!props.configured()) {
            throw IntegrationsExceptions.oauthNotConfigured();
        }
        stateService.verify(command.state(), command.organizationId(), command.requestedBy());

        OAuthTokens tokens = oauth.exchangeCode(command.code());
        List<Site> sites = oauth.accessibleResources(tokens.accessToken());
        if (sites.isEmpty()) {
            // No Jira site is reachable with the granted consent — treat as an auth failure.
            throw IntegrationsExceptions.oauthStateInvalid("no accessible Jira sites for the granted consent");
        }

        Site chosen = selectSite(sites, command.cloudId());
        if (chosen == null) {
            // Multiple sites and no cloudId yet: let the frontend choose. Nothing is persisted.
            return JiraOAuthCallbackResult.needsSiteSelection(sites);
        }

        if (connections.existsByOrganizationIdAndProviderAndStatusNot(
                command.organizationId(), IntegrationProviderType.JIRA, ConnectionStatus.DISCONNECTED)) {
            throw IntegrationsExceptions.alreadyConnected(
                    command.organizationId(), IntegrationProviderType.JIRA.name());
        }

        Instant now = Instant.now();
        Instant accessExpiresAt = now.plusSeconds(tokens.expiresInSeconds());
        IntegrationConnection connection = IntegrationConnection.oauth(
                command.organizationId(), IntegrationProviderType.JIRA,
                chosen.url(), chosen.cloudId(), tokens.refreshToken(),
                tokens.accessToken(), accessExpiresAt, now);
        return JiraOAuthCallbackResult.saved(connections.save(connection));
    }

    /**
     * Chooses the site to connect: the one matching {@code requestedCloudId} if given (or throws if it is
     * not among the accessible sites), otherwise the sole site when exactly one exists, otherwise null to
     * signal that the caller must pick.
     */
    private static Site selectSite(List<Site> sites, String requestedCloudId) {
        if (requestedCloudId != null && !requestedCloudId.isBlank()) {
            return sites.stream()
                    .filter(s -> s.cloudId().equals(requestedCloudId))
                    .findFirst()
                    .orElseThrow(() -> IntegrationsExceptions.oauthStateInvalid(
                            "chosen cloudId is not among the accessible sites"));
        }
        return sites.size() == 1 ? sites.get(0) : null;
    }
}
