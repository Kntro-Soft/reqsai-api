package com.kntro.reqsai.gateway.infrastructure.persistence.adapters;

import com.kntro.reqsai.gateway.application.port.IntegrationConnectionRepository;
import com.kntro.reqsai.gateway.domain.model.ConnectionStatus;
import com.kntro.reqsai.gateway.domain.model.IntegrationConnection;
import com.kntro.reqsai.gateway.domain.model.IntegrationProviderType;
import com.kntro.reqsai.gateway.infrastructure.persistence.repositories.IntegrationConnectionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adapts the {@link IntegrationConnectionRepository} port to Spring Data JPA. */
@Component
@RequiredArgsConstructor
public class IntegrationConnectionRepositoryAdapter implements IntegrationConnectionRepository {

    private final IntegrationConnectionJpaRepository jpa;

    @Override
    public IntegrationConnection save(IntegrationConnection connection) {
        return jpa.save(connection);
    }

    @Override
    public Optional<IntegrationConnection> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<IntegrationConnection> findByIdAndOrganizationId(UUID id, UUID organizationId) {
        return jpa.findByIdAndOrganizationId(id, organizationId);
    }

    @Override
    public List<IntegrationConnection> findAllByOrganizationId(UUID organizationId) {
        return jpa.findAllByOrganizationIdOrderByCreatedAtDesc(organizationId);
    }

    @Override
    public boolean existsByOrganizationIdAndProviderAndStatusNot(
            UUID organizationId, IntegrationProviderType provider, ConnectionStatus status) {
        return jpa.existsByOrganizationIdAndProviderAndStatusNot(organizationId, provider, status);
    }

    @Override
    public void delete(IntegrationConnection connection) {
        jpa.delete(connection);
    }
}
