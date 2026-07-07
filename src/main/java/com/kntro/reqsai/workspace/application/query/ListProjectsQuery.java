package com.kntro.reqsai.workspace.application.query;

import com.kntro.reqsai.shared.interfaces.pagination.PageCriteria;

import java.util.UUID;

/** Query to list projects for an organization, with optional pagination and sorting. */
public record ListProjectsQuery(UUID organizationId, UUID requestedBy, PageCriteria criteria) {
}
