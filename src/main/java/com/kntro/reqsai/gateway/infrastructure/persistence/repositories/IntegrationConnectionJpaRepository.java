package com.kntro.reqsai.gateway.infrastructure.persistence.repositories;

import com.kntro.reqsai.gateway.domain.model.ConnectionStatus;
import com.kntro.reqsai.gateway.domain.model.IntegrationConnection;
import com.kntro.reqsai.gateway.domain.model.IntegrationProviderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IntegrationConnectionJpaRepository extends JpaRepository<IntegrationConnection, UUID> {

    Optional<IntegrationConnection> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<IntegrationConnection> findAllByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    boolean existsByOrganizationIdAndProviderAndStatusNot(
            UUID organizationId, IntegrationProviderType provider, ConnectionStatus status);
}
