package com.kntro.reqsai.discovery.domain.model;

/**
 * Lifecycle status of a {@link DiscoverySession} — the full domain vocabulary of states (kept here even
 * though the creation slice only reaches {@code DRAFT}; the transition <em>methods</em> are added with
 * their own use cases — recording, processing).
 * <pre>
 *   STREAMING:  DRAFT ──start──▶ RECORDING ⇄ PAUSED ──stop──▶ STOPPED ──┐
 *   BATCH:      DRAFT ──uploadTranscript──▶ DRAFT(transcript) ──────────┤ startProcessing
 *                                                                       ▼
 *                                                  PROCESSING ──complete──▶ COMPLETED
 *                                                       └────fail────────▶ FAILED
 *               (COMPLETED | FAILED | STOPPED) ──reset──▶ DRAFT
 * </pre>
 * The live AI-suggestion loop runs <strong>during {@code RECORDING}</strong> in parallel and does not
 * change this status — it produces {@code Suggestion}s, not session-state transitions.
 */
public enum SessionStatus {

    /** Created, not yet recording; a pre-recorded transcript can be uploaded (batch/demo path). */
    DRAFT,

    /** Live audio capture in progress (STT streaming); the live suggestion loop runs in this state. */
    RECORDING,

    /** Recording temporarily suspended; resumes back to {@code RECORDING}. */
    PAUSED,

    /** Recording finished and the transcript assembled from the segments; ready to process. */
    STOPPED,

    /** AI extraction is running over the transcript. */
    PROCESSING,

    /** AI generation finished successfully. */
    COMPLETED,

    /** AI generation failed; the reason is in {@code processingError}. */
    FAILED
}
