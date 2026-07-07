package com.kntro.reqsai.discovery.infrastructure.persistence.repositories;

import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.SessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link DiscoverySession} (tenant-scoped table {@code discovery_sessions}). */
public interface DiscoverySessionJpaRepository extends JpaRepository<DiscoverySession, UUID> {

    Page<DiscoverySession> findAllByProjectId(UUID projectId, Pageable pageable);

    /** The project's active session (at most one — enforced by the partial unique index). */
    Optional<DiscoverySession> findFirstByProjectIdAndStatusIn(UUID projectId, List<SessionStatus> statuses);
}
