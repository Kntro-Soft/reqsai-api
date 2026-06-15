package com.kntro.reqsai.discovery.infrastructure.persistence.adapters;

import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.infrastructure.persistence.repositories.DiscoverySessionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Adapts the {@link DiscoverySessionRepository} port to Spring Data JPA. */
@Repository
@RequiredArgsConstructor
public class DiscoverySessionRepositoryAdapter implements DiscoverySessionRepository {

    private final DiscoverySessionJpaRepository jpa;

    @Override
    public DiscoverySession save(DiscoverySession session) {
        return jpa.save(session);
    }
}
