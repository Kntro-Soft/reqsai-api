package com.kntro.reqsai.discovery.infrastructure.persistence.repositories;

import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Spring Data repository for {@link TranscriptSegment} (tenant-scoped table {@code transcript_segments}). */
public interface TranscriptSegmentJpaRepository extends JpaRepository<TranscriptSegment, UUID> {

    List<TranscriptSegment> findAllBySessionIdOrderBySequenceAsc(UUID sessionId);

    void deleteAllBySessionId(UUID sessionId);
}
