package com.kntro.reqsai.gateway.application.handler;

import com.kntro.reqsai.gateway.application.port.IntegrationConnectionRepository;
import com.kntro.reqsai.gateway.application.port.IntegrationProvider;
import com.kntro.reqsai.gateway.application.query.TestConnectionQuery;
import com.kntro.reqsai.gateway.application.result.ConnectionTestResult;
import com.kntro.reqsai.gateway.application.service.ProviderCredentialsFactory;
import com.kntro.reqsai.gateway.application.service.ProviderRegistry;
import com.kntro.reqsai.gateway.domain.exception.IntegrationsExceptions;
import com.kntro.reqsai.gateway.domain.model.IntegrationConnection;
import com.kntro.reqsai.shared.domain.exception.InfrastructureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Re-verifies a connection's stored credential against the provider. Returns {@code ok=true} + the
 * account name and marks the connection verified on success; on an auth/reachability failure it marks
 * the connection {@code DEGRADED} and returns {@code ok=false} (a test never fails the request).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TestConnectionQueryHandler {

    private final IntegrationConnectionRepository connections;
    private final ProviderRegistry providers;
    private final ProviderCredentialsFactory credentials;

    @Transactional
    public ConnectionTestResult handle(TestConnectionQuery query) {
        IntegrationConnection connection = connections
                .findByIdAndOrganizationId(query.connectionId(), query.organizationId())
                .orElseThrow(() -> IntegrationsExceptions.connectionNotFound(query.connectionId()));

        IntegrationProvider provider = providers.get(connection.getProvider());
        try {
            String accountName = provider.verify(credentials.from(connection));
            connection.markVerified(Instant.now());
            connections.save(connection);
            return new ConnectionTestResult(true, accountName);
        } catch (InfrastructureException e) {
            log.warn("Connection {} verification failed [{}]", connection.getId(), e.error().code());
            connection.markDegraded();
            connections.save(connection);
            return new ConnectionTestResult(false, null);
        }
    }
}
