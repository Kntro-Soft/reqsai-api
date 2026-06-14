package com.kntro.reqsai.workspace.domain.model;

/**
 * Lifecycle of an {@link Organization} (a tenant account).
 * <p>
 * {@code PENDING} covers the window between persisting the registry row and finishing tenant-schema
 * provisioning: the {@code TenantSchemaResolver} excludes {@code PENDING} orgs so a half-provisioned
 * tenant never resolves to a schema. It flips to {@code ACTIVE} once provisioning succeeds.
 */
public enum OrgStatus {
    PENDING,
    ACTIVE,
    INACTIVE,
    DELETED
}
