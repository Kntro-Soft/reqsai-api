package com.kntro.reqsai.workspace.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Project document resource; the file fields are set only for uploaded client documents")
public record ProjectDocumentResponse(
        UUID id,
        UUID projectId,
        String name,
        String documentType,
        String status,
        @Schema(description = "Original file name of an uploaded document", nullable = true)
        String fileName,
        @Schema(description = "Media type of an uploaded document", nullable = true, example = "application/pdf")
        String mediaType,
        @Schema(description = "Size in bytes of an uploaded document", nullable = true)
        Long sizeBytes,
        @Schema(description = "Characters of text extracted from an uploaded document", nullable = true)
        Integer extractedChars,
        @Schema(description = "Project context summary the AI reads", nullable = true)
        String summary,
        Instant createdAt,
        Instant updatedAt
) {}
