package com.kntro.reqsai.workspace.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Project document metadata resource")
public record ProjectDocumentResponse(
        UUID id,
        UUID projectId,
        String name,
        String documentType,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
