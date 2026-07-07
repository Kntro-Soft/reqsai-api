package com.kntro.reqsai.integrations.application.port;

import com.kntro.reqsai.integrations.domain.model.ConnectionStatus;
import com.kntro.reqsai.integrations.domain.model.IntegrationConnection;
import com.kntro.reqsai.integrations.domain.model.IntegrationProviderType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for the {@link IntegrationConnection} aggregate. Tenant-scoped. */
public interface IntegrationConnectionRepository {

    IntegrationConnection save(IntegrationConnection connection);

    Optional<IntegrationConnection> findById(UUID id);

    Optional<IntegrationConnection> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<IntegrationConnection> findAllByOrganizationId(UUID organizationId);

    boolean existsByOrganizationIdAndProviderAndStatusNot(
            UUID organizationId, IntegrationProviderType provider, ConnectionStatus status);

    void delete(IntegrationConnection connection);
}
