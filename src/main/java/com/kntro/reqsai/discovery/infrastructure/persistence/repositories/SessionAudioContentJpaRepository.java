package com.kntro.reqsai.discovery.infrastructure.persistence.repositories;

import com.kntro.reqsai.discovery.domain.model.SessionAudioContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * Spring Data repository for {@link SessionAudioContent} (tenant-scoped table
 * {@code session_audio_contents}).
 */
public interface SessionAudioContentJpaRepository extends JpaRepository<SessionAudioContent, UUID> {

    /**
     * Bulk delete, so the (large) audio bytes are never loaded just to be dropped.
     */
    @Modifying
    @Query("delete from SessionAudioContent c where c.sessionId = :sessionId")
    int deleteBySessionId(@Param("sessionId") UUID sessionId);
}
