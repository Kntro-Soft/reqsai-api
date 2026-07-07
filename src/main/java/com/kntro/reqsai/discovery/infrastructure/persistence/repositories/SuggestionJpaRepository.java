package com.kntro.reqsai.discovery.infrastructure.persistence.repositories;

import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.SuggestionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link Suggestion} (tenant-scoped table {@code suggestions}). */
public interface SuggestionJpaRepository extends JpaRepository<Suggestion, UUID> {

    Optional<Suggestion> findByIdAndSessionId(UUID id, UUID sessionId);

    /**
     * Same lookup as {@link #findByIdAndSessionId} but takes a {@code PESSIMISTIC_WRITE} row lock so
     * concurrent accept/dismiss of the same suggestion serialize — the second transaction blocks,
     * then re-reads the already-resolved row and the domain guard rejects it (no duplicate story).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Suggestion s where s.id = :id and s.sessionId = :sessionId")
    Optional<Suggestion> findByIdAndSessionIdForUpdate(@Param("id") UUID id, @Param("sessionId") UUID sessionId);

    List<Suggestion> findAllBySessionIdAndStatus(UUID sessionId, SuggestionStatus status);

    Page<Suggestion> findAllByProjectIdAndStatus(UUID projectId, SuggestionStatus status, Pageable pageable);

    void deleteAllBySessionId(UUID sessionId);
}
