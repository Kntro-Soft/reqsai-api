package com.kntro.reqsai.codebase.domain.model;

/** Where the index of a connected repository stands. */
public enum CodeRepositoryStatus {

    /** Connected; indexing has not started yet. */
    PENDING,

    /** The archive is being read and its modules summarized. */
    INDEXING,

    /** The module map is ready for the copilot. */
    READY,

    /** The last indexing failed; the reason is kept in {@code error}. */
    FAILED;

    /** True while an indexing run owns the repository. */
    public boolean isRunning() {
        return this == PENDING || this == INDEXING;
    }
}
