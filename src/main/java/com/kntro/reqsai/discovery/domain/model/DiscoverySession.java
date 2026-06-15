package com.kntro.reqsai.discovery.domain.model;

import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.Assert;
import com.kntro.reqsai.shared.domain.valueobjects.LanguageCode;
import com.kntro.reqsai.shared.infrastructure.persistence.converters.LanguageCodeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate root of a requirements-elicitation session.
 */
@Entity
@Table(name = "discovery_sessions")
@Getter
public class DiscoverySession extends AggregateRoot {

    private static final int TITLE_MAX = 200;

    @Column(name = "project_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "title", nullable = false, length = TITLE_MAX)
    private String title;

    @Convert(converter = LanguageCodeConverter.class)
    @Column(name = "language", nullable = false, length = 8)
    private LanguageCode language;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SessionStatus status;

    @Column(name = "transcript", columnDefinition = "TEXT")
    private String transcript;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "audio_duration_ms", nullable = false)
    private long audioDurationMs = 0;

    @Column(name = "last_sequence", nullable = false)
    private int lastSequence = 0;

    @Column(name = "processing_error", length = 1000)
    private String processingError;

    protected DiscoverySession() {
        super();
    }

    public DiscoverySession(UUID projectId, String title, LanguageCode language) {
        super();
        this.projectId = Assert.notNull(projectId, "projectId");
        this.title = Assert.maxLength(Assert.notBlank(title, "title"), "title", TITLE_MAX);
        this.language = Assert.notNull(language, "language");
        this.status = SessionStatus.DRAFT;
    }
}
