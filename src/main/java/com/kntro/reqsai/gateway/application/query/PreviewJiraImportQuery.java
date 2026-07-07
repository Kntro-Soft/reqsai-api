package com.kntro.reqsai.gateway.application.query;

import java.util.UUID;

/**
 * Query for the Jira import preview: lists the candidate issues of the project's configured target and
 * flags likely duplicates, without creating anything.
 *
 * @param projectId   the project whose target defines WHERE to pull from
 * @param requestedBy caller id (authorization already enforced at the controller)
 */
public record PreviewJiraImportQuery(UUID projectId, UUID requestedBy) {}
