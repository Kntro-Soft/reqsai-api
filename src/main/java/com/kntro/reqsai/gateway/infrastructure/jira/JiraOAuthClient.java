package com.kntro.reqsai.gateway.infrastructure.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kntro.reqsai.gateway.application.config.JiraOAuthProperties;
import com.kntro.reqsai.gateway.infrastructure.exception.IntegrationsInfrastructureExceptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Outbound Atlassian OAuth 2.0 (3LO) client (ADR-0022): authorization-code exchange, refresh-token
 * rotation, and accessible-resources discovery. Mirrors the {@link JiraClient} RestClient style (per-call
 * client, typed records, status → infrastructure exception).
 * <ul>
 *   <li>Token exchange / refresh: {@code POST https://auth.atlassian.com/oauth/token} (JSON).</li>
 *   <li>Sites: {@code GET https://api.atlassian.com/oauth/token/accessible-resources} (Bearer access).</li>
 * </ul>
 * Any non-2xx on token exchange/refresh maps to {@code JIRA_OAUTH_EXCHANGE_FAILED}; a 401/403 on
 * accessible-resources maps to {@code JIRA_AUTH_FAILED}. Tokens are never logged nor put in exceptions.
 */
@Component
@Slf4j
public class JiraOAuthClient {

    private static final String TOKEN_URL = "https://auth.atlassian.com/oauth/token";
    private static final String RESOURCES_URL = "https://api.atlassian.com/oauth/token/accessible-resources";

    private final RestClient restClient = RestClient.create();
    private final JiraOAuthProperties props;

    public JiraOAuthClient(JiraOAuthProperties props) {
        this.props = props;
    }

    /** Exchanges an authorization {@code code} for the initial token set. */
    public OAuthTokens exchangeCode(String code) {
        Map<String, Object> body = Map.of(
                "grant_type", "authorization_code",
                "client_id", nn(props.clientId()),
                "client_secret", nn(props.clientSecret()),
                "code", code,
                "redirect_uri", nn(props.redirectUri()));
        return postToken(body, "exchangeCode");
    }

    /** Exchanges a {@code refreshToken} for a new (rotated) token set. */
    public OAuthTokens refresh(String refreshToken) {
        Map<String, Object> body = Map.of(
                "grant_type", "refresh_token",
                "client_id", nn(props.clientId()),
                "client_secret", nn(props.clientSecret()),
                "refresh_token", refreshToken);
        return postToken(body, "refresh");
    }

    /** Lists the Atlassian sites the access token can reach ({cloudId, url, name}). */
    public List<AccessibleResource> accessibleResources(String accessToken) {
        try {
            List<AccessibleResource> sites = restClient.get()
                    .uri(RESOURCES_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        if (res.getStatusCode().value() == 401 || res.getStatusCode().value() == 403) {
                            throw IntegrationsInfrastructureExceptions.jiraAuthFailed();
                        }
                        throw IntegrationsInfrastructureExceptions.jiraOauthExchangeFailed(
                                "accessible-resources HTTP " + res.getStatusCode().value(), null);
                    })
                    .body(RESOURCE_LIST);
            return sites == null ? List.of() : sites;
        } catch (com.kntro.reqsai.shared.domain.exception.InfrastructureException mapped) {
            throw mapped;
        } catch (Exception e) {
            log.warn("Jira accessible-resources failed: {}", e.getMessage());
            throw IntegrationsInfrastructureExceptions.jiraOauthExchangeFailed("accessible-resources", e);
        }
    }

    private OAuthTokens postToken(Map<String, Object> body, String op) {
        try {
            OAuthTokens tokens = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw IntegrationsInfrastructureExceptions.jiraOauthExchangeFailed(
                                op + " HTTP " + res.getStatusCode().value(), null);
                    })
                    .body(OAuthTokens.class);
            if (tokens == null || tokens.accessToken() == null) {
                throw IntegrationsInfrastructureExceptions.jiraOauthExchangeFailed(op + ": empty token response", null);
            }
            return tokens;
        } catch (com.kntro.reqsai.shared.domain.exception.InfrastructureException mapped) {
            throw mapped;
        } catch (Exception e) {
            log.warn("Jira OAuth {} failed: {}", op, e.getMessage());
            throw IntegrationsInfrastructureExceptions.jiraOauthExchangeFailed(op, e);
        }
    }

    private static String nn(String value) {
        return value == null ? "" : value;
    }

    private static final org.springframework.core.ParameterizedTypeReference<List<AccessibleResource>> RESOURCE_LIST =
            new org.springframework.core.ParameterizedTypeReference<>() {};

    /**
     * Atlassian token response. {@code refreshToken} is present when {@code offline_access} was requested;
     * on a rotating-refresh-token app it is a NEW value on every refresh (persist it).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OAuthTokens(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") long expiresIn,
            @JsonProperty("scope") String scope) {}

    /** One accessible Atlassian site: {@code id} is the cloud id used in the OAuth API base URL. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AccessibleResource(String id, String url, String name) {}
}
