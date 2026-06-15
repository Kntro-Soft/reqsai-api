package com.kntro.reqsai.discovery.application.query;

import com.kntro.reqsai.shared.interfaces.pagination.PageCriteria;

import java.util.UUID;

/** Query to list discovery sessions for a project, with optional pagination and sorting. */
public record ListProjectSessionsQuery(UUID projectId, PageCriteria criteria) {
}
