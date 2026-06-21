package com.kntro.reqsai.discovery.infrastructure.persistence.repositories;

import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.SuggestionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link Suggestion} (tenant-scoped table {@code suggestions}). */
public interface SuggestionJpaRepository extends JpaRepository<Suggestion, UUID> {

    Optional<Suggestion> findByIdAndSessionId(UUID id, UUID sessionId);

    List<Suggestion> findAllBySessionIdAndStatus(UUID sessionId, SuggestionStatus status);
}
