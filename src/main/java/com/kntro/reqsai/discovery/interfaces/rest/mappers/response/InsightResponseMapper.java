package com.kntro.reqsai.discovery.interfaces.rest.mappers.response;

import com.kntro.reqsai.discovery.domain.model.CodeFinding;
import com.kntro.reqsai.discovery.domain.model.CodeReference;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.CodeReferenceResponse;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.SuggestionCodeResponse;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.SuggestionEvidenceResponse;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Maps the evidence and the code insight of a suggestion (REST and live messages share the shape). */
public final class InsightResponseMapper {

    private InsightResponseMapper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static @Nullable SuggestionEvidenceResponse evidence(@Nullable Integer sequence, @Nullable String quote) {
        return quote == null || quote.isBlank() ? null : new SuggestionEvidenceResponse(sequence, quote);
    }

    public static @Nullable SuggestionCodeResponse code(@Nullable CodeFinding finding, @Nullable String note,
                                                        @Nullable List<CodeReference> references) {
        List<CodeReferenceResponse> refs = references(references);
        if (finding == null && refs.isEmpty()) return null;
        return new SuggestionCodeResponse(finding == null ? null : finding.name(), note, refs);
    }

    public static List<CodeReferenceResponse> references(@Nullable List<CodeReference> references) {
        if (references == null) return List.of();
        return references.stream()
                .map(r -> new CodeReferenceResponse(r.repository(), r.path(), r.name(), r.url()))
                .toList();
    }
}
