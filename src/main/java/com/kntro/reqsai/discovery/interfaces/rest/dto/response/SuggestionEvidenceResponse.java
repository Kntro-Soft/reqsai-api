package com.kntro.reqsai.discovery.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@Schema(description = "Where the suggestion was said: the verbatim fragment and the transcript segment holding it")
public record SuggestionEvidenceResponse(
        @Schema(description = "Sequence of the session's transcript segment, when it was located", nullable = true)
        @Nullable Integer sequence,
        @Schema(description = "Verbatim fragment of the conversation") String quote
) {
}
