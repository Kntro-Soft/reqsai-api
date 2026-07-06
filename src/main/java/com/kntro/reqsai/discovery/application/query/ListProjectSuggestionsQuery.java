package com.kntro.reqsai.discovery.application.query;

import com.kntro.reqsai.discovery.domain.model.SuggestionStatus;
import com.kntro.reqsai.shared.interfaces.pagination.PageCriteria;

import java.util.UUID;

/**
 * Query to list a project's suggestions filtered by review status, paginated — e.g. the "N pending
 * from previous sessions" view. Defaults to {@code PENDING} when {@code status} is {@code null}.
 */
public record ListProjectSuggestionsQuery(UUID projectId, SuggestionStatus status, PageCriteria criteria) {

    public SuggestionStatus statusOrDefault() {
        return status != null ? status : SuggestionStatus.PENDING;
    }
}
