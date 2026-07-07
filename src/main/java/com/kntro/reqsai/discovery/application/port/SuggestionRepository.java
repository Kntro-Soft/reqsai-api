package com.kntro.reqsai.discovery.application.port;

import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.SuggestionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SuggestionRepository {

    Suggestion save(Suggestion suggestion);

    Optional<Suggestion> findById(UUID id);

    Optional<Suggestion> findByIdAndSessionId(UUID id, UUID sessionId);

    Optional<Suggestion> findByIdAndSessionIdForUpdate(UUID id, UUID sessionId);

    List<Suggestion> findAllBySessionIdAndStatus(UUID sessionId, SuggestionStatus status);

    /** Paginated suggestions of a project filtered by review status (project-wide backlog triage). */
    Page<Suggestion> findAllByProjectIdAndStatus(UUID projectId, SuggestionStatus status, Pageable pageable);

    void deleteAllBySessionId(UUID sessionId);
}
