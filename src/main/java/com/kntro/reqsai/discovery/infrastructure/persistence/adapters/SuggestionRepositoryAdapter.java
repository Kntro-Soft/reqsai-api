package com.kntro.reqsai.discovery.infrastructure.persistence.adapters;

import com.kntro.reqsai.discovery.application.port.SuggestionRepository;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.SuggestionStatus;
import com.kntro.reqsai.discovery.infrastructure.persistence.repositories.SuggestionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adapts {@link SuggestionRepository} port to Spring Data JPA. */
@Repository
@RequiredArgsConstructor
public class SuggestionRepositoryAdapter implements SuggestionRepository {

    private final SuggestionJpaRepository jpa;

    @Override
    public Suggestion save(Suggestion suggestion) {
        return jpa.save(suggestion);
    }

    @Override
    public Optional<Suggestion> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<Suggestion> findByIdAndSessionId(UUID id, UUID sessionId) {
        return jpa.findByIdAndSessionId(id, sessionId);
    }

    @Override
    public List<Suggestion> findAllBySessionIdAndStatus(UUID sessionId, SuggestionStatus status) {
        return jpa.findAllBySessionIdAndStatus(sessionId, status);
    }
}
