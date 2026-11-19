package com.kntro.reqsai.discovery.domain.model;

/**
 * What became of the recording a session was created from (US69). The
 * transcript and the stories of a session outlive its audio: once the retention
 * period runs out the audio is deleted, and the session says so instead of
 * offering it.
 */
public enum AudioState {

    /**
     * No audio was kept: a live capture is streamed to the transcriber, or the
     * organization keeps none.
     */
    NONE,

    /**
     * The uploaded recording is stored and will be deleted when the retention
     * period runs out.
     */
    STORED,

    /** The recording was deleted by the organization's retention policy. */
    PURGED
}
