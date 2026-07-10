package com.kntro.reqsai.discovery.application.port;

import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
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

    /**
     * Atomically advances the realtime-suggestion watermark and cadence timestamp with a scoped UPDATE,
     * without rewriting the rest of the aggregate.
     * <p>
     * Critical for correctness: the realtime-suggestion pass loads the session, spends seconds in the
     * LLM call, then must persist only the watermark. Saving the whole aggregate would carry a stale
     * {@code last_sequence} and clobber the value the concurrent transcript-append path advanced in the
     * meantime — corrupting the segment sequence and breaking live transcription (duplicate-key on
     * {@code (session_id, sequence)}). This targeted update never touches {@code last_sequence}. The
     * watermark never moves backwards.
     *
     * @param sessionId         the session to update
     * @param suggestedSequence the new watermark (highest final segment turned into suggestions)
     * @param suggestedAt       the instant of this suggestion pass
     */
    void advanceSuggestionWatermark(UUID sessionId, int suggestedSequence, Instant suggestedAt);
}
