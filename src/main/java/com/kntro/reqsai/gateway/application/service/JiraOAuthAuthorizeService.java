package com.kntro.reqsai.gateway.application.service;

import com.kntro.reqsai.gateway.domain.exception.IntegrationsExceptions;
import com.kntro.reqsai.gateway.application.config.JiraOAuthProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

/**
 * Builds the Atlassian authorize URL for the OAuth 2.0 (3LO) flow (ADR-0022):
 * {@code https://auth.atlassian.com/authorize?audience=api.atlassian.com&client_id=...&scope=...&
 * redirect_uri=...&state=...&response_type=code&prompt=consent}. The {@code state} is a stateless signed
 * token from {@link JiraOAuthStateService}. When OAuth is not configured it raises
 * {@code JIRA_OAUTH_NOT_CONFIGURED} so the UI can disable the button.
 */
@Component
public class JiraOAuthAuthorizeService {

    private static final String AUTHORIZE_URL = "https://auth.atlassian.com/authorize";
    /** offline_access yields a refresh token; read:me + jira scopes cover verify/list/create. */
    private static final String SCOPES = "read:jira-work write:jira-work read:jira-user offline_access read:me";

    private final JiraOAuthProperties props;
    private final JiraOAuthStateService stateService;

    public JiraOAuthAuthorizeService(JiraOAuthProperties props, JiraOAuthStateService stateService) {
        this.props = props;
        this.stateService = stateService;
    }

    /** Builds the authorize URL + signed state for {@code orgId}/{@code userId}. */
    public AuthorizeUrl build(UUID orgId, UUID userId) {
        if (!props.configured()) {
            throw IntegrationsExceptions.oauthNotConfigured();
        }
        String state = stateService.issue(orgId, userId);
        String url = UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("audience", "api.atlassian.com")
                .queryParam("client_id", props.clientId())
                .queryParam("scope", SCOPES)
                .queryParam("redirect_uri", props.redirectUri())
                .queryParam("state", state)
                .queryParam("response_type", "code")
                .queryParam("prompt", "consent")
                .encode()
                .toUriString();
        return new AuthorizeUrl(url, state);
    }

    /** The built authorize URL and the signed state embedded in it. */
    public record AuthorizeUrl(String url, String state) {}
}
