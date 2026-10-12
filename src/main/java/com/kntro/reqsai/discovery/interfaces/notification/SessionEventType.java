package com.kntro.reqsai.discovery.interfaces.notification;

import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionRealtimeMessage;

/**
 * Typed discriminator carried in the {@code type} field of every {@link SessionRealtimeMessage}.
 * <p>
 * All session updates travel on a single topic; the client switches on this value to decide how to
 * render the message. Using an enum instead of bare strings keeps producer and consumer contracts
 * in lockstep and makes the exhaustive set of event kinds explicit and compile-time-safe.
 */
public enum SessionEventType {

    // Session lifecycle (project-level topic)

    /** A discovery session was created in {@code DRAFT} (broadcast on the project topic). */
    SESSION_CREATED,

    // Recording lifecycle

    /** Live recording started ({@code DRAFT → RECORDING}). */
    RECORDING_STARTED,

    /** Live recording paused ({@code RECORDING → PAUSED}). */
    RECORDING_PAUSED,

    /** Live recording resumed ({@code PAUSED → RECORDING}). */
    RECORDING_RESUMED,

    /** Live recording stopped ({@code RECORDING/PAUSED → STOPPED}). */
    RECORDING_STOPPED,

    // Live streaming capture

    /** A finalized transcript segment was appended during {@code RECORDING} (transcript-out). */
    TRANSCRIPT_SEGMENT,

    // AI processing lifecycle

    /** A transcript was attached via file upload ({@code DRAFT → STOPPED}). */
    TRANSCRIPT_UPLOADED,

    /** AI extraction started ({@code STOPPED/FAILED → PROCESSING}). */
    PROCESSING,

    /** AI extraction finished successfully ({@code PROCESSING → COMPLETED}). */
    COMPLETED,

    /** AI extraction failed ({@code PROCESSING → FAILED}); carries the failure reason. */
    FAILED,

    /** A user story was generated from the session and persisted. */
    STORY_GENERATED,

    // Suggestion review layer (realtime AI-to-analyst gate)

    /** A new suggestion was created and is pending analyst review. */
    SUGGESTION_GENERATED,

    /** The analyst accepted a suggestion (backlog was mutated). */
    SUGGESTION_ACCEPTED,

    /** The analyst dismissed a suggestion (no backlog change). */
    SUGGESTION_DISMISSED,

    /**
     * The deep verification of a suggestion against the code started, finished or failed; carries the code
     * finding, note, references and verification state to apply on the card.
     */
    SUGGESTION_CODE_UPDATED,

    // Speakers (diarization)

    /** The analyst named a diarized speaker or set their side (client / team); carries the new description. */
    SPEAKER_UPDATED,

    // AI allowance

    /**
     * The organization's monthly AI allowance is spent, so live suggestions are paused for this session.
     * Capture and the transcript keep working; suggestions resume once the allowance renews or the plan
     * is upgraded. Sent once per pause.
     */
    AI_QUOTA_PAUSED,

    // Live presence

    /**
     * The roster of users currently viewing the live session changed (someone joined or left).
     * Carries the full participant list so the client can render it idempotently.
     */
    PRESENCE_STATE
}
