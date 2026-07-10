package com.kntro.reqsai.gateway.domain.model;

/**
 * Lifecycle of an {@link IntegrationSyncJob}: born {@code RUNNING}, ends {@code COMPLETED} (per-item
 * failures allowed) or {@code FAILED} (fatal error, e.g. the tracker was unreachable). Persisted as
 * the wire value ({@code VARCHAR(16)}), so names are part of the API contract.
 */
public enum IntegrationSyncJobStatus {
    RUNNING,
    COMPLETED,
    FAILED
}
