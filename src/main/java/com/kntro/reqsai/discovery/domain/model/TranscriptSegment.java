package com.kntro.reqsai.discovery.domain.model;

import com.kntro.reqsai.shared.domain.model.AuditableEntity;
import com.kntro.reqsai.shared.domain.support.Assert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * A fragment of a session's live transcript, produced incrementally by the streaming STT pipeline
 * during {@code RECORDING}. Non-root entity of the {@link DiscoverySession} aggregate: created through
 * {@code DiscoverySession.recordSegment(...)} (which assigns {@code sequence} via {@code lastSequence})
 * and persisted by its own repository for query/replay.
 *
 * <p>The pair {@code (sessionId, sequence)} is unique — the unique index {@code uq_segments_session_sequence}
 * enforces idempotency at the DB level on reconnection. When a partial segment is later finalized,
 * call {@link #finalize(String)} rather than creating a new row.
 *
 * <p>{@code speakerLabel} is the diarization label when the provider supplies one (e.g. "0", "A").
 * {@code isFinal} is {@code false} for partial hypotheses and {@code true} for committed text.
 */
@Entity
@Table(name = "transcript_segments")
@Getter
public class TranscriptSegment extends AuditableEntity {

    private static final int SPEAKER_LABEL_MAX = 64;

    @Column(name = "session_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "sequence", nullable = false, updatable = false)
    private int sequence;

    @Column(name = "speaker_label", length = SPEAKER_LABEL_MAX)
    private @Nullable String speakerLabel;

    @Column(name = "text", columnDefinition = "TEXT", nullable = false)
    private String text;

    @Column(name = "start_ms", nullable = false, updatable = false)
    private long startMs;

    @Column(name = "end_ms", nullable = false, updatable = false)
    private long endMs;

    @Column(name = "is_final", nullable = false)
    private boolean isFinal;

    protected TranscriptSegment() {
        super();
    }

    public TranscriptSegment(UUID sessionId, int sequence, @Nullable String speakerLabel, String text, long startMs, long endMs, boolean isFinal) {
        super();
        this.sessionId = Assert.notNull(sessionId, "sessionId");
        Assert.isTrue(sequence > 0, "sequence", "sequence must be positive");
        this.sequence = sequence;
        this.speakerLabel = speakerLabel != null ? Assert.maxLength(speakerLabel, "speakerLabel", SPEAKER_LABEL_MAX) : null;
        this.text = Assert.notBlank(text, "text");
        Assert.isTrue(startMs >= 0, "startMs", "startMs must be >= 0");
        Assert.isTrue(endMs >= startMs, "endMs", "endMs must be >= startMs");
        this.startMs = startMs;
        this.endMs = endMs;
        this.isFinal = isFinal;
    }

    /** Replaces the partial hypothesis text with the finalized transcription and marks the segment as final. */
    public void finalize(String finalText) {
        this.text = Assert.notBlank(finalText, "finalText");
        this.isFinal = true;
    }
}
