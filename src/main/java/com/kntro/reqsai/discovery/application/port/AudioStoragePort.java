package com.kntro.reqsai.discovery.application.port;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Where the recording of a session is kept until its organization's retention
 * period runs out (US69). Tenant-scoped: the active schema is the caller's
 * tenant. Whether a session still has an audio, and when it was deleted, is
 * recorded on the session itself; this port only holds and drops the bytes.
 */
public interface AudioStoragePort {

    /** Stores the recording of {@code sessionId}. */
    void store(UUID sessionId, @Nullable String fileName, byte[] content);

    /**
     * Deletes the recording of {@code sessionId}. Idempotent: deleting a recording
     * that is not there does nothing.
     */
    void delete(UUID sessionId);
}
