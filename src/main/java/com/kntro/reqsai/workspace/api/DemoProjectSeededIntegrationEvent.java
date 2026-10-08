package com.kntro.reqsai.workspace.api;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Public integration event announcing that Workspace has (re)seeded the workspace-owned sample content of
 * an organization's demo project (the project profile, glossary and constraints) — on onboarding, or when
 * someone restores the demo data. Exposed on the {@code workspace::api} named interface so Discovery can
 * (re)seed its own sample content (a finished session with its transcript, user stories and pending
 * suggestions) without Workspace reaching into Discovery's internals.
 * <p>
 * Published <strong>synchronously</strong> inside the seeding transaction with the tenant already bound,
 * so a consumer that listens with a plain {@code @EventListener} joins that transaction: the whole demo
 * (re)seed commits or rolls back as one unit, and the restore endpoint answers only once every module has
 * finished. Consumers must replace their previous demo content (wipe, then seed), so a repeated event is
 * harmless.
 *
 * @param organizationId the organization (tenant) that owns the demo project
 * @param projectId      the demo project
 * @param requestedBy    the user the sample content is attributed to (the org owner on onboarding, the
 *                       caller on a restore)
 * @param occurredAt     when the seeding happened
 */
public record DemoProjectSeededIntegrationEvent(
        UUID organizationId,
        UUID projectId,
        UUID requestedBy,
        Instant occurredAt
) implements Serializable {

    public static DemoProjectSeededIntegrationEvent of(UUID organizationId, UUID projectId, UUID requestedBy) {
        return new DemoProjectSeededIntegrationEvent(organizationId, projectId, requestedBy, Instant.now());
    }
}
