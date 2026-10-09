package com.kntro.reqsai.discovery.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Schema(description = "What the client's connected code says about the suggestion")
public record SuggestionCodeResponse(
        @Schema(allowableValues = {"ALREADY_EXISTS", "CONFLICTS_WITH_CODE"}, nullable = true) @Nullable String finding,
        @Schema(description = "What the code does, or both sides of the conflict", nullable = true) @Nullable String note,
        @Schema(description = "Modules of the code it relates to") List<CodeReferenceResponse> references
) {
}
