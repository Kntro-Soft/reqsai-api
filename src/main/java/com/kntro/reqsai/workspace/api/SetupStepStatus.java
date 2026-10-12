package com.kntro.reqsai.workspace.api;

/** Where one step of a project's setup stands. */
public enum SetupStepStatus {

    /** Requested, not started. */
    PENDING,

    /** Under way. */
    RUNNING,

    /** Finished. */
    DONE,

    /** Could not finish; it can be retried. */
    FAILED,

    /** The user chose not to wait for it. */
    SKIPPED;

    /** Whether the step still has work ahead. */
    public boolean isOpen() {
        return this == PENDING || this == RUNNING;
    }
}
