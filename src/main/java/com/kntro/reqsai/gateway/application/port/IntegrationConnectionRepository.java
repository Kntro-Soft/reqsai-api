package com.kntro.reqsai.gateway.application.port;

import com.kntro.reqsai.gateway.domain.model.ConnectionStatus;
import com.kntro.reqsai.gateway.domain.model.IntegrationConnection;
import com.kntro.reqsai.gateway.domain.model.IntegrationProviderType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for the {@link IntegrationConnection} aggregate. Tenant-scoped. */
public interface IntegrationConnectionRepository {

    IntegrationConnection save(IntegrationConnection connection);

    Optional<IntegrationConnection> findById(UUID id);

    Optional<IntegrationConnection> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<IntegrationConnection> findAllByOrganizationId(UUID organizationId);

    /** Whether the organization already has a live (not disconnected) connection to this very site. */
    boolean existsActiveBySite(UUID organizationId, IntegrationProviderType provider, String siteUrl);

    void delete(IntegrationConnection connection);
}
