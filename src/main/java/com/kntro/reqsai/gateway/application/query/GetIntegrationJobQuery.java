package com.kntro.reqsai.gateway.application.query;

import java.util.UUID;

/** Fetches one integration sync job of a project by id (404 when absent or in another project). */
public record GetIntegrationJobQuery(UUID projectId, UUID jobId, UUID requestedBy) {}
