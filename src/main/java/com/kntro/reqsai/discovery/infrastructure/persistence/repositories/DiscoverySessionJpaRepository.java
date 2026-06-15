package com.kntro.reqsai.discovery.infrastructure.persistence.repositories;

import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Spring Data repository for {@link DiscoverySession} (tenant-scoped table {@code discovery_sessions}). */
public interface DiscoverySessionJpaRepository extends JpaRepository<DiscoverySession, UUID> {

    Page<DiscoverySession> findAllByProjectId(UUID projectId, Pageable pageable);
}
