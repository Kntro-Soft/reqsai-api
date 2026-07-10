package com.kntro.reqsai.gateway.application.query;

import java.util.UUID;

/**
 * Lists a project's integration sync jobs. {@code activeOnly} limits the result to RUNNING jobs
 * (the reload-recovery path for the global progress banner); otherwise the most recent ~10 jobs of
 * any status are returned, newest first.
 */
public record ListIntegrationJobsQuery(UUID projectId, boolean activeOnly, UUID requestedBy) {}
