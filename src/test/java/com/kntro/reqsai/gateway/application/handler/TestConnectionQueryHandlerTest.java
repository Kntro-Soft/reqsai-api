package com.kntro.reqsai.gateway.application.handler;

import com.kntro.reqsai.gateway.application.port.IntegrationConnectionRepository;
import com.kntro.reqsai.gateway.application.port.IntegrationProvider;
import com.kntro.reqsai.gateway.application.query.TestConnectionQuery;
import com.kntro.reqsai.gateway.application.result.ConnectionTestResult;
import com.kntro.reqsai.gateway.application.service.ProviderCredentialsFactory;
import com.kntro.reqsai.gateway.application.service.ProviderRegistry;
import com.kntro.reqsai.gateway.domain.model.ConnectionStatus;
import com.kntro.reqsai.gateway.domain.model.IntegrationConnection;
import com.kntro.reqsai.gateway.domain.model.IntegrationProviderType;
import com.kntro.reqsai.gateway.infrastructure.exception.IntegrationsInfrastructureExceptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("Application: Test connection")
@ExtendWith(MockitoExtension.class)
class TestConnectionQueryHandlerTest {

    @Mock private IntegrationConnectionRepository connections;
    @Mock private IntegrationProvider jiraProvider;
    @Mock private ProviderCredentialsFactory credentialsFactory;

    private TestConnectionQueryHandler handler;

    @BeforeEach
    void setUp() {
        when(jiraProvider.type()).thenReturn(IntegrationProviderType.JIRA);
        handler = new TestConnectionQueryHandler(
                connections, new ProviderRegistry(List.of(jiraProvider)), credentialsFactory);
    }

    @Test
    @DisplayName("returns ok + account name and marks the connection verified on success")
    void ok_on_success() {
        UUID orgId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        IntegrationConnection connection = connection(orgId);
        when(connections.findByIdAndOrganizationId(connectionId, orgId)).thenReturn(Optional.of(connection));
        when(credentialsFactory.from(connection)).thenReturn(
                new IntegrationProvider.ProviderCredentials("https://acme.atlassian.net", "pm@acme.com", "tok"));
        when(jiraProvider.verify(any())).thenReturn("Jane Admin");

        ConnectionTestResult result = handler.handle(new TestConnectionQuery(orgId, connectionId, UUID.randomUUID()));

        assertThat(result.ok()).isTrue();
        assertThat(result.accountName()).isEqualTo("Jane Admin");
        assertThat(connection.getStatus()).isEqualTo(ConnectionStatus.CONNECTED);
    }

    @Test
    @DisplayName("returns ok=false and marks DEGRADED on verification failure (never fails the request)")
    void degraded_on_failure() {
        UUID orgId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        IntegrationConnection connection = connection(orgId);
        when(connections.findByIdAndOrganizationId(connectionId, orgId)).thenReturn(Optional.of(connection));
        when(credentialsFactory.from(connection)).thenReturn(
                new IntegrationProvider.ProviderCredentials("https://acme.atlassian.net", "pm@acme.com", "tok"));
        when(jiraProvider.verify(any())).thenThrow(IntegrationsInfrastructureExceptions.jiraAuthFailed());

        ConnectionTestResult result = handler.handle(new TestConnectionQuery(orgId, connectionId, UUID.randomUUID()));

        assertThat(result.ok()).isFalse();
        assertThat(result.accountName()).isNull();
        assertThat(connection.getStatus()).isEqualTo(ConnectionStatus.DEGRADED);
    }

    private static IntegrationConnection connection(UUID orgId) {
        return new IntegrationConnection(orgId, IntegrationProviderType.JIRA,
                "https://acme.atlassian.net", "pm@acme.com", "tok", Instant.now());
    }
}
