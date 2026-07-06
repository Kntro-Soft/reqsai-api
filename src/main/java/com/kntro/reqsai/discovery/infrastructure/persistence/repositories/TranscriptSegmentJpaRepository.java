package com.kntro.reqsai.discovery.infrastructure.persistence.repositories;

import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** Spring Data repository for {@link TranscriptSegment} (tenant-scoped table {@code transcript_segments}). */
public interface TranscriptSegmentJpaRepository extends JpaRepository<TranscriptSegment, UUID> {

    List<TranscriptSegment> findAllBySessionIdOrderBySequenceAsc(UUID sessionId);

    @Query("SELECT ts FROM TranscriptSegment ts WHERE ts.sessionId = :sessionId AND ts.isFinal = true ORDER BY ts.sequence DESC")
    List<TranscriptSegment> findRecentFinalBySessionId(@Param("sessionId") UUID sessionId, Pageable pageable);

    @Query("SELECT ts FROM TranscriptSegment ts WHERE ts.sessionId = :sessionId AND ts.isFinal = true AND ts.sequence > :afterSequence ORDER BY ts.sequence ASC")
    List<TranscriptSegment> findFinalBySessionIdAfter(@Param("sessionId") UUID sessionId, @Param("afterSequence") int afterSequence);

    @Query("SELECT ts FROM TranscriptSegment ts WHERE ts.sessionId = :sessionId AND ts.isFinal = true AND ts.sequence < :beforeSequence ORDER BY ts.sequence DESC")
    List<TranscriptSegment> findFinalBySessionIdBefore(@Param("sessionId") UUID sessionId, @Param("beforeSequence") int beforeSequence, Pageable pageable);

    long countBySessionIdAndIsFinalTrue(UUID sessionId);

    void deleteAllBySessionId(UUID sessionId);
}
