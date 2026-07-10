package com.kntro.reqsai.gateway.domain.model;

/**
 * What an {@link IntegrationSyncJob} does: {@code IMPORT} pulls tracker issues into the backlog as
 * user stories; {@code PUSH_ALL} exports every project story to the tracker. Persisted as the wire
 * value ({@code VARCHAR(16)}), so names are part of the API contract.
 */
public enum IntegrationSyncJobType {
    IMPORT,
    PUSH_ALL
}
