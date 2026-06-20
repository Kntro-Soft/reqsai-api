package com.kntro.reqsai.workspace.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Glossary term resource")
public record GlossaryTermResponse(

        @Schema(description = "Glossary term unique identifier", example = "019756a0-1234-7abc-8def-000000000301")
        UUID id,

        @Schema(description = "Business term used in the client domain", example = "Lead")
        String term,

        @Schema(description = "Definition of the term in the context of the project")
        String definition,

        @Schema(description = "Timestamp when the term was manually added", example = "2026-06-20T13:30:00Z")
        Instant addedAt,

        @Schema(description = "Timestamp when the resource was created", example = "2026-06-20T13:30:00Z")
        Instant createdAt,

        @Schema(description = "Timestamp of the last update", example = "2026-06-20T13:30:00Z")
        Instant updatedAt
) {}
