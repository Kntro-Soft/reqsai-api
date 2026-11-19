package com.kntro.reqsai.discovery.infrastructure.persistence.adapters;

import com.kntro.reqsai.discovery.application.port.AudioStoragePort;
import com.kntro.reqsai.discovery.domain.model.SessionAudioContent;
import com.kntro.reqsai.discovery.infrastructure.persistence.repositories.SessionAudioContentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Keeps the recordings as {@code bytea} in the tenant schema, like the avatars:
 * no extra infrastructure to run, tenant isolation for free and the delete is
 * transactional with the session's purge mark. Swap this adapter for an
 * object-store one to move the bytes out of the database.
 */
@Repository
@RequiredArgsConstructor
public class DatabaseAudioStorageAdapter implements AudioStoragePort {

    private final SessionAudioContentJpaRepository jpa;

    @Override
    public void store(UUID sessionId, @Nullable String fileName, byte[] content) {
        jpa.save(new SessionAudioContent(sessionId, fileName, content));
    }

    @Override
    public void delete(UUID sessionId) {
        jpa.deleteBySessionId(sessionId);
    }
}
