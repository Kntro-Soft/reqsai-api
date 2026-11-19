package com.kntro.reqsai.discovery.domain.model;

import com.kntro.reqsai.discovery.domain.event.*;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryError;
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
import org.jspecify.annotations.Nullable;

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

    /** Highest final-segment sequence already turned into realtime suggestions (watermark). */
    @Column(name = "last_suggested_sequence", nullable = false)
    private int lastSuggestedSequence = 0;

    /**
     * Wall-clock instant of the last realtime suggestion pass that actually ran generation.
     * Drives the time-based cadence fallback: a pass fires once enough seconds have elapsed with
     * new transcript even if the char threshold has not been reached, so short back-and-forth
     * exchanges stream instead of arriving as one late batch. {@code null} until the first pass.
     */
    @Column(name = "last_suggested_at")
    private @Nullable Instant lastSuggestedAt;

    /** Whether the assistant analyzes on its own ({@link SuggestionMode#AUTO}) or only when asked. */
    @Column(name = "auto_suggest", nullable = false)
    private boolean autoSuggest = true;

    @Column(name = "processing_error", length = PROCESSING_ERROR_MAX)
    private String processingError;

    /** When the analyst confirmed that the participants consented to the recording (Ley N.° 29733); {@code null} until confirmed. */
    @Column(name = "recording_consent_at")
    private @Nullable Instant recordingConsentAt;

    /** The user who confirmed the participants' consent; {@code null} until confirmed. */
    @Column(name = "recording_consent_by", columnDefinition = "uuid")
    private @Nullable UUID recordingConsentBy;
    /** When the uploaded recording was stored; {@code null} when no audio was kept for this session. */
    @Column(name = "audio_stored_at")
    private @Nullable Instant audioStoredAt;

    /** When the retention policy deleted the stored recording; {@code null} while it is still there. */
    @Column(name = "audio_purged_at")
    private @Nullable Instant audioPurgedAt;

    protected DiscoverySession() {
        super();
    }

    public DiscoverySession(UUID projectId, String title, LanguageCode language) {
        super();
        this.projectId = Assert.notNull(projectId, "projectId");
        this.title = Assert.maxLength(Assert.notBlank(title, "title"), "title", TITLE_MAX);
        this.language = Assert.notNull(language, "language");
        this.status = SessionStatus.DRAFT;
        this.startedAt = Instant.now();
        registerEvent(DiscoverySessionCreatedEvent.of(getId(), projectId, this.title, language.value(), this.startedAt));
    }

    /** Batch/demo path: saves the pre-recorded transcript and transitions {@code DRAFT → STOPPED}. */
    public void uploadTranscript(String transcript, long audioDurationMs) {
        uploadTranscript(transcript, audioDurationMs, 0);
    }

    /**
     * Batch path with the recording's diarized utterances kept as {@code segmentCount} final segments,
     * numbered 1..{@code segmentCount}: {@code lastSequence} points at the last one, as after a live capture.
     */
    public void uploadTranscript(String transcript, long audioDurationMs, int segmentCount) {
        Assert.isTrue(this.status == SessionStatus.DRAFT, "status", "uploadTranscript requires DRAFT but was " + this.status, DiscoveryError.INVALID_SESSION_STATUS);
        Assert.isTrue(segmentCount >= 0, "segmentCount", "segmentCount must be >= 0");
        this.transcript = Assert.notBlank(transcript, "transcript");
        this.audioDurationMs = audioDurationMs;
        this.lastSequence = segmentCount;
        this.status = SessionStatus.STOPPED;
        this.endedAt = Instant.now();
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
     * Records that the analyst confirmed the participants consented to the recording, with who and when.
     * Only before the capture starts ({@code DRAFT}). Idempotent: the first confirmation is kept so the
     * audit trail never moves.
     */
    public void confirmRecordingConsent(UUID userId, Instant now) {
        Assert.notNull(userId, "userId");
        Assert.notNull(now, "now");
        Assert.isTrue(this.status == SessionStatus.DRAFT, "status",
                "confirmRecordingConsent requires DRAFT but was " + this.status, DiscoveryError.INVALID_SESSION_STATUS);
        if (hasRecordingConsent()) {
            return;
        }
        this.recordingConsentBy = userId;
        this.recordingConsentAt = now;
    }

    /** Whether the participants' consent to the recording has been confirmed. */
    public boolean hasRecordingConsent() {
        return this.recordingConsentAt != null && this.recordingConsentBy != null;
    }

    /** Guard of the live capture start: rejects with {@link DiscoveryError#RECORDING_CONSENT_REQUIRED} until consent is confirmed. */
    public void requireRecordingConsent() {
        Assert.isTrue(hasRecordingConsent(), "recordingConsent",
                "the participants' consent to the recording must be confirmed before the capture starts",
                DiscoveryError.RECORDING_CONSENT_REQUIRED);
    }

    /** Live recording path: {@code DRAFT → RECORDING}. Overrides {@code startedAt} to the actual recording start. */
    public void startRecording(Instant now) {
        Assert.isTrue(this.status == SessionStatus.DRAFT, "status", "startRecording requires DRAFT but was " + this.status, DiscoveryError.INVALID_SESSION_STATUS);
        this.status = SessionStatus.RECORDING;
        this.startedAt = Assert.notNull(now, "now");
        registerEvent(DiscoverySessionRecordingStartedEvent.of(getId(), projectId, title, language.value(), startedAt));
    }

    /** Temporarily pauses recording: {@code RECORDING → PAUSED}. */
    public void pauseRecording() {
        Assert.isTrue(this.status == SessionStatus.RECORDING,
                "status", "pauseRecording requires RECORDING but was " + this.status,
                DiscoveryError.INVALID_SESSION_STATUS);
        this.status = SessionStatus.PAUSED;
        registerEvent(DiscoverySessionRecordingPausedEvent.of(getId(), projectId, title, language.value(), startedAt));
    }

    /** Resumes a paused recording: {@code PAUSED → RECORDING}. */
    public void resumeRecording() {
        Assert.isTrue(this.status == SessionStatus.PAUSED,
                "status", "resumeRecording requires PAUSED but was " + this.status,
                DiscoveryError.INVALID_SESSION_STATUS);
        this.status = SessionStatus.RECORDING;
        registerEvent(DiscoverySessionRecordingResumedEvent.of(getId(), projectId, title, language.value(), startedAt));
    }

    /**
     * Records a transcript segment during live capture: requires {@code RECORDING}, advances
     * {@code lastSequence} (only for finals — partials reuse the current value), and raises
     * {@link TranscriptSegmentAppendedEvent} carrying the
     * full segment payload for the realtime transcript-out push.
     *
     * @param isFinal {@code true} for a committed segment (increment sequence); {@code false} for a
     *                live hypothesis (re-uses the current {@code lastSequence} so the client updates in place)
     * @return the sequence assigned to this segment
     */
    public int recordSegment(String text, @Nullable String speakerLabel, long startMs, long endMs, boolean isFinal) {
        Assert.isTrue(this.status == SessionStatus.RECORDING, "status", "recordSegment requires RECORDING but was " + this.status, DiscoveryError.INVALID_SESSION_STATUS);
        String clean = Assert.notBlank(text, "text");
        Assert.isTrue(startMs >= 0, "startMs", "startMs must be >= 0");
        Assert.isTrue(endMs >= startMs, "endMs", "endMs must be >= startMs");
        if (isFinal) {
            this.lastSequence += 1;
        }
        registerEvent(TranscriptSegmentAppendedEvent.of(getId(), this.lastSequence, speakerLabel, clean, startMs, endMs, isFinal));
        return this.lastSequence;
    }

    /** Stops the live recording: {@code RECORDING} or {@code PAUSED → STOPPED}. */
    public void stopRecording(Instant now) {
        Assert.isTrue(this.status == SessionStatus.RECORDING || this.status == SessionStatus.PAUSED, "status", "stopRecording requires RECORDING or PAUSED but was " + this.status, DiscoveryError.INVALID_SESSION_STATUS);
        this.status = SessionStatus.STOPPED;
        this.endedAt = Assert.notNull(now, "now");
        registerEvent(DiscoverySessionRecordingStoppedEvent.of(getId(), projectId, title, language.value(), startedAt));
    }

    /** Records that the uploaded recording was stored, which starts its retention period. */
    public void retainAudio(Instant now) {
        Assert.notNull(now, "now");
        Assert.isTrue(this.audioStoredAt == null, "audioStoredAt", "the session already has a stored audio",
                DiscoveryError.AUDIO_NOT_PURGEABLE);
        this.audioStoredAt = now;
    }

    /** Whether the recording is still stored, was deleted by the retention policy, or was never kept. */
    public AudioState getAudioState() {
        if (audioStoredAt == null) {
            return AudioState.NONE;
        }
        return audioPurgedAt == null ? AudioState.STORED : AudioState.PURGED;
    }

    /**
     * Whether the retention purge may delete this session's audio: it is stored, was stored at or before
     * {@code storedBefore} (now minus the retention period) and the session is not in progress.
     */
    public boolean isAudioPurgeable(Instant storedBefore) {
        return getAudioState() == AudioState.STORED
                && !audioStoredAt.isAfter(storedBefore)
                && !status.isInProgress();
    }

    /**
     * Marks the stored recording as deleted by the retention policy. The transcript, the segments and the
     * stories of the session are not touched. Refused when there is no stored audio (so a repeated purge is
     * a no-op for the caller to skip) or while the session is still in progress.
     */
    public void purgeAudio(Instant now) {
        Assert.notNull(now, "now");
        Assert.isTrue(getAudioState() == AudioState.STORED, "audioPurgedAt",
                "purgeAudio requires a stored audio but it was " + getAudioState(), DiscoveryError.AUDIO_NOT_PURGEABLE);
        Assert.isTrue(!status.isInProgress(), "status",
                "the audio of a session in progress cannot be purged but the session is " + this.status,
                DiscoveryError.AUDIO_NOT_PURGEABLE);
        this.audioPurgedAt = now;
    }

    /** Advances the realtime-suggestion watermark; never moves it backwards. */
    public void advanceSuggestedSequence(int sequence) {
        if (sequence > this.lastSuggestedSequence) {
            this.lastSuggestedSequence = sequence;
        }
    }

    /** When the assistant analyzes this session's conversation (US46). */
    public SuggestionMode getSuggestionMode() {
        return autoSuggest ? SuggestionMode.AUTO : SuggestionMode.MANUAL;
    }

    /**
     * Lets the analyst choose when the assistant analyzes: on its own or only on demand. Allowed until
     * the recording ends ({@code DRAFT}, {@code RECORDING} or {@code PAUSED}).
     */
    public void changeSuggestionMode(SuggestionMode mode) {
        Assert.notNull(mode, "mode");
        Assert.isTrue(this.status == SessionStatus.DRAFT || isLive(), "status",
                "the suggestion mode can only change before or during the recording, but the session is " + this.status,
                DiscoveryError.INVALID_SESSION_STATUS);
        this.autoSuggest = mode == SuggestionMode.AUTO;
    }

    /** {@code RECORDING} or {@code PAUSED}: the meeting is still being captured. */
    public boolean isLive() {
        return this.status == SessionStatus.RECORDING || this.status == SessionStatus.PAUSED;
    }

    /** Records when the last realtime suggestion pass ran (drives the time-based cadence fallback). */
    public void markSuggestedAt(Instant when) {
        this.lastSuggestedAt = Assert.notNull(when, "when");
    }
}
