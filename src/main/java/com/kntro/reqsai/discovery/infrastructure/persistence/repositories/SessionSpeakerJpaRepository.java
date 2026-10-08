package com.kntro.reqsai.discovery.infrastructure.persistence.repositories;

import com.kntro.reqsai.discovery.domain.model.SessionSpeaker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link SessionSpeaker} (tenant-scoped table {@code session_speakers}). */
public interface SessionSpeakerJpaRepository extends JpaRepository<SessionSpeaker, UUID> {

    List<SessionSpeaker> findAllBySessionId(UUID sessionId);

    Optional<SessionSpeaker> findBySessionIdAndSpeakerLabel(UUID sessionId, String speakerLabel);
}
