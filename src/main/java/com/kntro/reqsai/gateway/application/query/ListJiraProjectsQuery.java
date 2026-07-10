package com.kntro.reqsai.gateway.application.query;

import java.util.UUID;

/** List the Jira projects visible to an organization connection. */
public record ListJiraProjectsQuery(UUID organizationId, UUID connectionId, UUID requestedBy) {}
