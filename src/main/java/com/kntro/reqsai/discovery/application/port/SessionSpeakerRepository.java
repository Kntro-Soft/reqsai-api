package com.kntro.reqsai.discovery.application.port;

import com.kntro.reqsai.discovery.domain.model.SessionSpeaker;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link SessionSpeaker}: what the analyst said about each diarized speaker of a
 * session. Tenant-scoped (schema resolved from the JWT {@code orgId}).
 */
public interface SessionSpeakerRepository {

    SessionSpeaker save(SessionSpeaker speaker);

    /** Every described speaker of a session (speakers the analyst never touched have no row). */
    List<SessionSpeaker> findAllBySessionId(UUID sessionId);

    Optional<SessionSpeaker> findBySessionIdAndSpeakerLabel(UUID sessionId, String speakerLabel);
}
