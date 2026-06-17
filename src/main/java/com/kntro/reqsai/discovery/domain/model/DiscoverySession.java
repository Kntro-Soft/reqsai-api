package com.kntro.reqsai.discovery.domain.model;

import com.kntro.reqsai.discovery.domain.event.DiscoverySessionCreatedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionProcessingCompletedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionProcessingFailedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionProcessingStartedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionTranscriptUploadedEvent;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryError;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
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
    private static final int STATUS_MAX = 16;
    private static final int PROCESSING_ERROR_MAX = 1000;

    @Column(name = "project_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "title", nullable = false, length = TITLE_MAX)
    private String title;

    @Convert(converter = LanguageCodeConverter.class)
    @Column(name = "language", nullable = false, length = LanguageCode.MAX_LENGTH)
    private LanguageCode language;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = STATUS_MAX)
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

    @Column(name = "processing_error", length = PROCESSING_ERROR_MAX)
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
        this.startedAt = java.time.Instant.now();
        registerEvent(DiscoverySessionCreatedEvent.of(getId(), projectId));
    }

    /** Batch/demo path: saves the pre-recorded transcript and transitions {@code DRAFT → STOPPED}. */
    public void uploadTranscript(String transcript, long audioDurationMs) {
        Assert.isTrue(this.status == SessionStatus.DRAFT, "status", "uploadTranscript requires DRAFT but was " + this.status, DiscoveryError.INVALID_SESSION_STATUS);
        this.transcript = Assert.notBlank(transcript, "transcript");
        this.audioDurationMs = audioDurationMs;
        this.status = SessionStatus.STOPPED;
        this.endedAt = java.time.Instant.now();
        registerEvent(DiscoverySessionTranscriptUploadedEvent.of(getId(), projectId));
    }

    /** Transitions {@code STOPPED} or {@code FAILED} → {@code PROCESSING} to begin AI extraction. */
    public void startProcessing() {
        Assert.isTrue(this.status == SessionStatus.STOPPED || this.status == SessionStatus.FAILED, "status", "startProcessing requires STOPPED or FAILED but was " + this.status, DiscoveryError.INVALID_SESSION_STATUS);
        this.status = SessionStatus.PROCESSING;
        this.processingError = null;
        registerEvent(DiscoverySessionProcessingStartedEvent.of(getId(), projectId));
    }

    /** Transitions {@code PROCESSING} → {@code COMPLETED} after successful AI extraction. */
    public void complete() {
        Assert.isTrue(this.status == SessionStatus.PROCESSING, "status", "complete requires PROCESSING but was " + this.status, DiscoveryError.INVALID_SESSION_STATUS);
        this.status = SessionStatus.COMPLETED;
        registerEvent(DiscoverySessionProcessingCompletedEvent.of(getId(), projectId));
    }

    /** Transitions {@code PROCESSING} → {@code FAILED} and stores the failure reason. */
    public void fail(String reason) {
        Assert.isTrue(this.status == SessionStatus.PROCESSING, "status", "fail requires PROCESSING but was " + this.status, DiscoveryError.INVALID_SESSION_STATUS);
        this.status = SessionStatus.FAILED;
        this.processingError = Assert.maxLength(Assert.notBlank(reason, "reason"), "reason", PROCESSING_ERROR_MAX);
        registerEvent(DiscoverySessionProcessingFailedEvent.of(getId(), projectId, reason));
    }

    /**
     * Starts recording for this session, transitioning the status to RECORDING.
     * Allowed only when the session is in DRAFT status.
     */
    public void startRecording() {
        if (status != SessionStatus.DRAFT) {
            throw DiscoveryExceptions.invalidTransition(status, "startRecording");
        }
        this.status = SessionStatus.RECORDING;
        this.startedAt = Instant.now();
        this.processingError = null;
    }
}
