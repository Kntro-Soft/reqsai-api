package com.kntro.reqsai.discovery.infrastructure.persistence.adapters;

import com.kntro.reqsai.discovery.application.port.TranscriptSegmentRepository;
import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import com.kntro.reqsai.discovery.infrastructure.persistence.repositories.TranscriptSegmentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Adapts the {@link TranscriptSegmentRepository} port to Spring Data JPA. */
@Repository
@RequiredArgsConstructor
public class TranscriptSegmentRepositoryAdapter implements TranscriptSegmentRepository {

    private final TranscriptSegmentJpaRepository jpa;

    @Override
    public TranscriptSegment save(TranscriptSegment segment) {
        return jpa.save(segment);
    }

    @Override
    public List<TranscriptSegment> findAllBySessionId(UUID sessionId) {
        return jpa.findAllBySessionIdOrderBySequenceAsc(sessionId);
    }

    @Override
    @Transactional
    public void deleteAllBySessionId(UUID sessionId) {
        jpa.deleteAllBySessionId(sessionId);
    }
}
