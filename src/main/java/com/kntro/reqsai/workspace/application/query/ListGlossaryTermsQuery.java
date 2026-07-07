package com.kntro.reqsai.workspace.application.query;

import com.kntro.reqsai.shared.interfaces.pagination.PageCriteria;

import java.util.UUID;

/**
 * Query to list a project's glossary terms, paginated with an optional case-insensitive {@code search}
 * over term + definition. An absent/blank search returns the whole (paginated) glossary.
 */
public record ListGlossaryTermsQuery(
        UUID organizationId,
        UUID projectId,
        UUID requestedBy,
        PageCriteria criteria,
        String search
) {}
