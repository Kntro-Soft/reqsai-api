package com.kntro.reqsai.workspace.application.query;

import com.kntro.reqsai.shared.interfaces.pagination.PageCriteria;

import java.util.UUID;

/**
 * Query to list a project's constraints, paginated with an optional case-insensitive {@code search}
 * over description. An absent/blank search returns the whole (paginated) constraint list.
 */
public record ListProjectConstraintsQuery(
        UUID organizationId,
        UUID projectId,
        UUID requestedBy,
        PageCriteria criteria,
        String search
) {}
