package com.kntro.reqsai.gateway.application.handler;

import com.kntro.reqsai.gateway.application.command.JiraOAuthCallbackCommand;
import com.kntro.reqsai.gateway.application.config.JiraOAuthProperties;
import com.kntro.reqsai.gateway.application.port.IntegrationConnectionRepository;
import com.kntro.reqsai.gateway.application.port.JiraOAuthPort;
import com.kntro.reqsai.gateway.application.port.JiraOAuthPort.OAuthTokens;
import com.kntro.reqsai.gateway.application.port.JiraOAuthPort.Site;
import com.kntro.reqsai.gateway.application.result.JiraOAuthCallbackResult;
import com.kntro.reqsai.gateway.application.service.JiraOAuthPendingTokenCache;
import com.kntro.reqsai.gateway.application.service.JiraOAuthStateService;
import com.kntro.reqsai.gateway.domain.model.ConnectionStatus;
import com.kntro.reqsai.gateway.domain.model.CredentialType;
import com.kntro.reqsai.gateway.domain.model.IntegrationConnection;
import com.kntro.reqsai.gateway.domain.model.IntegrationProviderType;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Application: Jira OAuth callback")
@ExtendWith(MockitoExtension.class)
class JiraOAuthCallbackCommandHandlerTest {

    private static final JiraOAuthProperties PROPS = new JiraOAuthProperties(
            "client-id", "client-secret", "https://cb", "state-secret-material-0123456789");

    @Mock
    private JiraOAuthPort oauth;
    @Mock
    private IntegrationConnectionRepository connections;

    private JiraOAuthStateService stateService;
    private JiraOAuthPendingTokenCache pendingTokens;
    private JiraOAuthCallbackCommandHandler handler;

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        stateService = new JiraOAuthStateService(PROPS);
        pendingTokens = new JiraOAuthPendingTokenCache(); // real cache to exercise the two-step flow
        handler = new JiraOAuthCallbackCommandHandler(PROPS, stateService, oauth, connections, pendingTokens);
    }

    private JiraOAuthCallbackCommand command(String cloudId) {
        return new JiraOAuthCallbackCommand(orgId, "auth-code", stateService.issue(orgId, userId), cloudId, userId);
    }

    private JiraOAuthCallbackCommand command(String state, String cloudId) {
        return new JiraOAuthCallbackCommand(orgId, "auth-code", state, cloudId, userId);
    }

    private OAuthTokens tokens() {
        return new OAuthTokens("access-abc", "refresh-xyz", 3600, "read:jira-work offline_access");
    }

    @Test
    @DisplayName("single accessible site auto-selects and persists an encrypted OAUTH2 connection")
    void single_site_auto_selects() {
        when(oauth.exchangeCode("auth-code")).thenReturn(tokens());
        when(oauth.accessibleResources("access-abc"))
                .thenReturn(List.of(new Site("cloud-1", "https://acme.atlassian.net", "Acme")));
        when(connections.existsByOrganizationIdAndProviderAndStatusNot(
                any(), any(), any())).thenReturn(false);
        when(connections.save(any(IntegrationConnection.class))).thenAnswer(i -> i.getArgument(0));

        JiraOAuthCallbackResult result = handler.handle(command(null));

        assertThat(result.isSaved()).isTrue();
        IntegrationConnection saved = result.connection();
        assertThat(saved.getCredentialType()).isEqualTo(CredentialType.OAUTH2);
        assertThat(saved.getCloudId()).isEqualTo("cloud-1");
        assertThat(saved.getSiteUrl()).isEqualTo("https://acme.atlassian.net");
        assertThat(saved.getEmail()).isNull();
        assertThat(saved.getOauthRefreshToken()).isEqualTo("refresh-xyz");
        assertThat(saved.getOauthAccessToken()).isEqualTo("access-abc");
        assertThat(saved.getStatus()).isEqualTo(ConnectionStatus.CONNECTED);
    }

    @Test
    @DisplayName("multiple sites without a cloudId returns the site list and saves nothing")
    void multi_site_returns_sites() {
        when(oauth.exchangeCode("auth-code")).thenReturn(tokens());
        when(oauth.accessibleResources("access-abc")).thenReturn(List.of(
                new Site("cloud-1", "https://acme.atlassian.net", "Acme"),
                new Site("cloud-2", "https://beta.atlassian.net", "Beta")));

        JiraOAuthCallbackResult result = handler.handle(command(null));

        assertThat(result.isSaved()).isFalse();
        assertThat(result.sites()).extracting(Site::cloudId).containsExactly("cloud-1", "cloud-2");
        verify(connections, never()).save(any());
    }

    @Test
    @DisplayName("two-step multi-site flow exchanges the single-use code exactly once and reuses the cache")
    void two_step_multi_site_exchanges_code_once() {
        when(oauth.exchangeCode("auth-code")).thenReturn(tokens());
        when(oauth.accessibleResources("access-abc")).thenReturn(List.of(
                new Site("cloud-1", "https://acme.atlassian.net", "Acme"),
                new Site("cloud-2", "https://beta.atlassian.net", "Beta")));
        when(connections.existsByOrganizationIdAndProviderAndStatusNot(any(), any(), any())).thenReturn(false);
        when(connections.save(any(IntegrationConnection.class))).thenAnswer(i -> i.getArgument(0));

        // Step 1: no cloudId -> returns sites, caches tokens under the state (nothing saved yet).
        String state = stateService.issue(orgId, userId);
        JiraOAuthCallbackResult first = handler.handle(command(state, null));
        assertThat(first.isSaved()).isFalse();
        verify(connections, never()).save(any());

        // Step 2: same state + chosen cloudId -> completes from the cache, saves, does NOT re-exchange.
        JiraOAuthCallbackResult second = handler.handle(command(state, "cloud-2"));
        assertThat(second.isSaved()).isTrue();
        assertThat(second.connection().getCloudId()).isEqualTo("cloud-2");

        // The single-use code was exchanged exactly once and accessible-resources called exactly once.
        verify(oauth, times(1)).exchangeCode("auth-code");
        verify(oauth, times(1)).accessibleResources("access-abc");
    }

    @Test
    @DisplayName("a chosen cloudId among multiple sites persists that site")
    void chosen_cloud_id_persists() {
        when(oauth.exchangeCode("auth-code")).thenReturn(tokens());
        when(oauth.accessibleResources("access-abc")).thenReturn(List.of(
                new Site("cloud-1", "https://acme.atlassian.net", "Acme"),
                new Site("cloud-2", "https://beta.atlassian.net", "Beta")));
        when(connections.existsByOrganizationIdAndProviderAndStatusNot(any(), any(), any())).thenReturn(false);
        when(connections.save(any(IntegrationConnection.class))).thenAnswer(i -> i.getArgument(0));

        JiraOAuthCallbackResult result = handler.handle(command("cloud-2"));

        assertThat(result.isSaved()).isTrue();
        assertThat(result.connection().getCloudId()).isEqualTo("cloud-2");
        assertThat(result.connection().getSiteUrl()).isEqualTo("https://beta.atlassian.net");
    }

    @Test
    @DisplayName("rejects a second active connection with a 409 domain error")
    void rejects_already_connected() {
        when(oauth.exchangeCode("auth-code")).thenReturn(tokens());
        when(oauth.accessibleResources("access-abc"))
                .thenReturn(List.of(new Site("cloud-1", "https://acme.atlassian.net", "Acme")));
        when(connections.existsByOrganizationIdAndProviderAndStatusNot(
                any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> handler.handle(command(null)))
                .isInstanceOf(DomainException.class);
        verify(connections, never()).save(any());
    }

    @Test
    @DisplayName("a tampered state is rejected before any token exchange")
    void rejects_invalid_state() {
        JiraOAuthCallbackCommand bad = new JiraOAuthCallbackCommand(
                orgId, "auth-code", "bogus.state", null, userId);

        assertThatThrownBy(() -> handler.handle(bad)).isInstanceOf(DomainException.class);
        verify(oauth, never()).exchangeCode(any());
    }

    @Test
    @DisplayName("unconfigured oauth is rejected with JIRA_OAUTH_NOT_CONFIGURED")
    void rejects_unconfigured() {
        JiraOAuthProperties unconfigured = new JiraOAuthProperties(null, null, null, null);
        JiraOAuthCallbackCommandHandler h = new JiraOAuthCallbackCommandHandler(
                unconfigured, stateService, oauth, connections, pendingTokens);

        assertThatThrownBy(() -> h.handle(command(null))).isInstanceOf(DomainException.class);
        verify(oauth, never()).exchangeCode(any());
    }
}
