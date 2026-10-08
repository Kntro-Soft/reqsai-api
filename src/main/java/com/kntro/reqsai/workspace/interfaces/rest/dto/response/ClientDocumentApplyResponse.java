package com.kntro.reqsai.workspace.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Outcome of applying a reviewed client document")
public record ClientDocumentApplyResponse(
        @Schema(description = "The document, now ACTIVE")
        ProjectDocumentResponse document,
        @Schema(description = "Glossary terms added", example = "4")
        int glossaryTermsAdded,
        @Schema(description = "Selected glossary terms skipped because the glossary already had them", example = "1")
        int glossaryTermsSkipped,
        @Schema(description = "Constraints added", example = "2")
        int constraintsAdded,
        @Schema(description = "Selected constraints skipped because the project already recorded them", example = "0")
        int constraintsSkipped
) {}
