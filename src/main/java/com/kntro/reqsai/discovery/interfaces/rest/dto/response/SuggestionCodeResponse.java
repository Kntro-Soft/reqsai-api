package com.kntro.reqsai.discovery.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

@Schema(description = "What the client's connected code says about the suggestion")
public record SuggestionCodeResponse(
        @Schema(allowableValues = {"ALREADY_EXISTS", "CONFLICTS_WITH_CODE"}, nullable = true) @Nullable String finding,
        @Schema(description = "What the code does, or both sides of the conflict", nullable = true) @Nullable String note,
        @Schema(description = "Modules, files and lines of the code it relates to") List<CodeReferenceResponse> references,
        @Schema(description = "Outcome of the last deep verification in the pure source code; null when never verified",
                allowableValues = {"ALREADY_EXISTS", "CONFLICTS_WITH_CODE", "NOT_FOUND", "UNSURE"}, nullable = true)
        @Nullable String verdict,
        @Schema(description = "Where the deep verification stands; null when none was asked",
                allowableValues = {"VERIFYING", "VERIFIED", "FAILED"}, nullable = true) @Nullable String verifyState,
        @Schema(description = "When the last deep verification finished", nullable = true) @Nullable Instant verifiedAt,
        @Schema(description = "Why the deep verification failed (verifyState FAILED)", nullable = true)
        @Nullable String verifyError
) {

    public SuggestionCodeResponse(@Nullable String finding, @Nullable String note,
                                  List<CodeReferenceResponse> references) {
        this(finding, note, references, null, null, null, null);
    }
}
