package com.kntro.reqsai.search.interfaces.rest.mappers.response;

import com.kntro.reqsai.search.interfaces.rest.dto.response.SearchHitResponse;
import com.kntro.reqsai.shared.application.search.SearchHit;

/** Maps the shared {@link SearchHit} value snapshot to the REST {@link SearchHitResponse}. */
public final class SearchHitResponseMapper {

    private SearchHitResponseMapper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static SearchHitResponse toResponse(SearchHit hit) {
        return new SearchHitResponse(
                hit.type().name(),
                hit.id(),
                hit.title(),
                hit.subtitle(),
                hit.projectId());
    }
}
