package com.kntro.reqsai.integrations.application.query;

import java.util.UUID;

/** List the Jira issue types for a project key visible to an organization connection. */
public record ListJiraIssueTypesQuery(UUID organizationId, UUID connectionId, String projectKey, UUID requestedBy) {}
