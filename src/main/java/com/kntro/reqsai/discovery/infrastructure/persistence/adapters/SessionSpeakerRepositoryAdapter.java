package com.kntro.reqsai.discovery.infrastructure.persistence.adapters;

import com.kntro.reqsai.discovery.application.port.SessionSpeakerRepository;
import com.kntro.reqsai.discovery.domain.model.SessionSpeaker;
import com.kntro.reqsai.discovery.infrastructure.persistence.repositories.SessionSpeakerJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adapts the {@link SessionSpeakerRepository} port to Spring Data JPA. */
@Repository
@RequiredArgsConstructor
public class SessionSpeakerRepositoryAdapter implements SessionSpeakerRepository {

    private final SessionSpeakerJpaRepository jpa;

    @Override
    public SessionSpeaker save(SessionSpeaker speaker) {
        return jpa.save(speaker);
    }

    @Override
    public List<SessionSpeaker> findAllBySessionId(UUID sessionId) {
        return jpa.findAllBySessionId(sessionId);
    }

    @Override
    public Optional<SessionSpeaker> findBySessionIdAndSpeakerLabel(UUID sessionId, String speakerLabel) {
        return jpa.findBySessionIdAndSpeakerLabel(sessionId, speakerLabel);
    }
}
