package com.kntro.reqsai.discovery.infrastructure.persistence.adapters;

import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.SessionStatus;
import com.kntro.reqsai.discovery.infrastructure.persistence.repositories.DiscoverySessionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adapts the {@link DiscoverySessionRepository} port to Spring Data JPA. */
@Repository
@RequiredArgsConstructor
public class DiscoverySessionRepositoryAdapter implements DiscoverySessionRepository {

    private final DiscoverySessionJpaRepository jpa;

    @Override
    public DiscoverySession save(DiscoverySession session) {
        return jpa.save(session);
    }

    @Override
    public Optional<DiscoverySession> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Page<DiscoverySession> findAllByProjectId(UUID projectId, Pageable pageable) {
        return jpa.findAllByProjectId(projectId, pageable);
    }

    @Override
    public Optional<DiscoverySession> findActiveByProjectId(UUID projectId) {
        return jpa.findFirstByProjectIdAndStatusIn(
                projectId, List.of(SessionStatus.RECORDING, SessionStatus.PAUSED));
    }

    @Override
    public void advanceSuggestionWatermark(UUID sessionId, int suggestedSequence, Instant suggestedAt) {
        jpa.advanceSuggestionWatermark(sessionId, suggestedSequence, suggestedAt);
    }
}
