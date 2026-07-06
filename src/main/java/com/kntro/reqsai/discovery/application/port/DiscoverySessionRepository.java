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

    /**
     * The project's currently active ({@code RECORDING} or {@code PAUSED}) session, if any. Backed by the
     * partial unique index {@code uq_sessions_project_active}, so at most one row can match — used to
     * enforce the single-active-session rule before starting or resuming another.
     */
    Optional<DiscoverySession> findActiveByProjectId(UUID projectId);
}
