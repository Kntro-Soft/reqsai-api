package com.kntro.reqsai.gateway.application.handler;

import com.kntro.reqsai.gateway.application.command.ConnectJiraCommand;
import com.kntro.reqsai.gateway.application.port.IntegrationConnectionRepository;
import com.kntro.reqsai.gateway.application.port.IntegrationProvider;
import com.kntro.reqsai.gateway.application.service.ProviderRegistry;
import com.kntro.reqsai.gateway.domain.model.ConnectionStatus;
import com.kntro.reqsai.gateway.domain.model.IntegrationConnection;
import com.kntro.reqsai.gateway.domain.model.IntegrationProviderType;
import com.kntro.reqsai.gateway.infrastructure.exception.IntegrationsInfrastructureExceptions;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.shared.domain.exception.InfrastructureException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Application: Connect Jira")
@ExtendWith(MockitoExtension.class)
class ConnectJiraCommandHandlerTest {

    @Mock
    private IntegrationConnectionRepository connections;
    @Mock
    private IntegrationProvider jiraProvider;

    private ConnectJiraCommandHandler handler;

    @BeforeEach
    void setUp() {
        when(jiraProvider.type()).thenReturn(IntegrationProviderType.JIRA);
        handler = new ConnectJiraCommandHandler(connections, new ProviderRegistry(List.of(jiraProvider)));
    }

    @Test
    @DisplayName("verifies the credential then persists an encrypted connection")
    void connects_after_verifying() {
        UUID orgId = UUID.randomUUID();
        when(connections.existsByOrganizationIdAndProviderAndStatusNot(
                eq(orgId), eq(IntegrationProviderType.JIRA), eq(ConnectionStatus.DISCONNECTED))).thenReturn(false);
        when(jiraProvider.verify(any())).thenReturn("Jane Admin");
        when(connections.save(any(IntegrationConnection.class))).thenAnswer(i -> i.getArgument(0));

        IntegrationConnection saved = handler.handle(new ConnectJiraCommand(
                orgId, "https://acme.atlassian.net/", "pm@acme.com", "tok", UUID.randomUUID()));

        verify(jiraProvider).verify(any());
        verify(connections).save(any(IntegrationConnection.class));
        assertThat(saved.getProvider()).isEqualTo(IntegrationProviderType.JIRA);
        assertThat(saved.getSiteUrl()).isEqualTo("https://acme.atlassian.net"); // trailing slash trimmed
        assertThat(saved.getStatus()).isEqualTo(ConnectionStatus.CONNECTED);
    }

    @Test
    @DisplayName("rejects a second active connection with a 409 domain error")
    void rejects_duplicate() {
        UUID orgId = UUID.randomUUID();
        when(connections.existsByOrganizationIdAndProviderAndStatusNot(
                eq(orgId), eq(IntegrationProviderType.JIRA), eq(ConnectionStatus.DISCONNECTED))).thenReturn(true);

        assertThatThrownBy(() -> handler.handle(new ConnectJiraCommand(
                orgId, "https://acme.atlassian.net", "pm@acme.com", "tok", UUID.randomUUID())))
                .isInstanceOf(DomainException.class);

        verify(jiraProvider, never()).verify(any());
        verify(connections, never()).save(any());
    }

    @Test
    @DisplayName("propagates a verification failure and persists nothing")
    void propagates_verify_failure() {
        UUID orgId = UUID.randomUUID();
        when(connections.existsByOrganizationIdAndProviderAndStatusNot(
                eq(orgId), eq(IntegrationProviderType.JIRA), eq(ConnectionStatus.DISCONNECTED))).thenReturn(false);
        when(jiraProvider.verify(any())).thenThrow(IntegrationsInfrastructureExceptions.jiraAuthFailed());

        assertThatThrownBy(() -> handler.handle(new ConnectJiraCommand(
                orgId, "https://acme.atlassian.net", "pm@acme.com", "bad", UUID.randomUUID())))
                .isInstanceOf(InfrastructureException.class);

        verify(connections, never()).save(any());
    }
}
