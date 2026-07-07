package com.kntro.reqsai.integrations.domain.model;

/**
 * Lifecycle of an {@link IntegrationConnection}. A connection is {@code CONNECTED} while its stored
 * credential last verified successfully; a failed verification flips it to {@code DEGRADED} without
 * losing the credential; deleting it removes the row. The partial unique index treats anything other
 * than {@code DISCONNECTED} as "active", so at most one active connection exists per org per provider.
 */
public enum ConnectionStatus {

    /** Credential present and last verification succeeded. */
    CONNECTED,

    /** Credential present but the last verification failed (auth or reachability). */
    DEGRADED,

    /** Retired connection (not counted by the single-active-connection unique index). */
    DISCONNECTED
}
