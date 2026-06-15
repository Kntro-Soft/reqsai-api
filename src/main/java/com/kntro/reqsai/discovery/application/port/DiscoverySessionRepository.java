package com.kntro.reqsai.discovery.application.port;

import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for the {@link DiscoverySession} aggregate. Tenant-scoped: the active schema is set
 * per request from the JWT {@code orgId}.
 */
public interface DiscoverySessionRepository {

    DiscoverySession save(DiscoverySession session);

    Optional<DiscoverySession> findById(UUID id);

    Page<DiscoverySession> findAllByProjectId(UUID projectId, Pageable pageable);
}
