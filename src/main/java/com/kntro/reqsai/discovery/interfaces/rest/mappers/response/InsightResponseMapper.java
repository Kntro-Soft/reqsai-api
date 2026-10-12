package com.kntro.reqsai.discovery.interfaces.rest.mappers.response;

import com.kntro.reqsai.discovery.domain.model.CodeFinding;
import com.kntro.reqsai.discovery.domain.model.CodeReference;
import com.kntro.reqsai.discovery.domain.model.CodeVerdict;
import com.kntro.reqsai.discovery.domain.model.CodeVerifyState;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.CodeReferenceResponse;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.SuggestionCodeResponse;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.SuggestionEvidenceResponse;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
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

    /** As {@link #code(CodeFinding, String, List)}, plus where the deep verification of the suggestion stands. */
    public static @Nullable SuggestionCodeResponse code(@Nullable CodeFinding finding, @Nullable String note,
                                                        @Nullable List<CodeReference> references,
                                                        @Nullable CodeVerifyState state,
                                                        @Nullable CodeVerdict verdict, @Nullable Instant verifiedAt,
                                                        @Nullable String error) {
        List<CodeReferenceResponse> refs = references(references);
        if (finding == null && refs.isEmpty() && state == null) return null;
        return new SuggestionCodeResponse(finding == null ? null : finding.name(), note, refs,
                verdict == null ? null : verdict.name(), state == null ? null : state.name(), verifiedAt, error);
    }

    public static List<CodeReferenceResponse> references(@Nullable List<CodeReference> references) {
        if (references == null) return List.of();
        return references.stream()
                .map(r -> new CodeReferenceResponse(r.repository(), r.path(), r.name(), r.url(), r.file(), r.lineStart(),
                        r.lineEnd(), r.quote()))
                .toList();
    }
}
