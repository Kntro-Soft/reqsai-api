package com.kntro.reqsai.workspace.api;

import com.kntro.reqsai.shared.domain.model.TenantAwareDomainEvent;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public integration event announcing that a project was created (or a step of its setup retried) with external
 * sources to wire up: link a Jira project, import its stories, connect and index repositories. Gateway and
 * Codebase react to it, each for the sources it owns, so Workspace never reaches into them.
 * <p>
 * Published inside the transaction that creates the project and delivered after it commits, in the background
 * (Spring Modulith), bound to the tenant of the request. A source that fails never undoes the project: its
 * module reports the failure through {@link ProjectSetupReporter}.
 *
 * @param organizationId the organization (tenant) that owns the project
 * @param projectId      the new project
 * @param requestedBy    the user who created it; the work is done on their behalf
 * @param sources        what the project asked for
 * @param steps          the {@link SetupStepKeys} to run: all the requested ones on creation, only the failed one
 *                       when the user retries it
 * @param tenant         the tenant to restore in the listener thread
 * @param occurredAt     when it was requested
 */
public record ProjectSetupRequestedIntegrationEvent(
        UUID organizationId,
        UUID projectId,
        UUID requestedBy,
        ProjectSources sources,
        List<String> steps,
        TenantContext.TenantSnapshot tenant,
        Instant occurredAt
) implements TenantAwareDomainEvent {

    public static ProjectSetupRequestedIntegrationEvent of(UUID organizationId, UUID projectId, UUID requestedBy,
                                                           ProjectSources sources, List<String> steps) {
        return new ProjectSetupRequestedIntegrationEvent(organizationId, projectId, requestedBy, sources, List.copyOf(steps),
                TenantContext.capture(), Instant.now());
    }

    public boolean runs(String stepKey) {
        return steps.contains(stepKey);
    }

    @Override
    public UUID aggregateId() {
        return projectId;
    }
}
