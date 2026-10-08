package com.kntro.reqsai.workspace.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "What the AI extracted from an uploaded client document, for the analyst to review")
public record ClientDocumentAnalysisResponse(
        @Schema(description = "Id of the stored document (PENDING until applied or discarded)")
        UUID documentId,
        @Schema(description = "Sanitized original file name", example = "Términos de referencia.pdf")
        String fileName,
        @Schema(description = "Detected media type", example = "application/pdf")
        String mediaType,
        @Schema(description = "File size in bytes", example = "184320")
        long sizeBytes,
        @Schema(description = "Characters of text extracted (after the 200,000-character cap)", example = "18452")
        int extractedChars,
        @Schema(description = "Whether the text was longer than the cap and was cut")
        boolean truncated,
        @Schema(description = "Whether the AI classified the text; when false the lists are empty and the context is an excerpt")
        boolean classified,
        @Schema(description = "Proposed document type", example = "TECHNICAL_SPEC")
        String documentType,
        @Schema(description = "Proposed project context summary")
        String context,
        @Schema(description = "Proposed glossary terms")
        List<GlossaryItem> glossary,
        @Schema(description = "Proposed project constraints")
        List<ConstraintItem> constraints
) {

    @Schema(description = "A proposed glossary term")
    public record GlossaryItem(
            String term,
            String definition,
            @Schema(description = "The project glossary already has this term")
            boolean exists) {}

    @Schema(description = "A proposed project constraint")
    public record ConstraintItem(
            String text,
            @Schema(description = "The project already records this constraint")
            boolean exists) {}
}
