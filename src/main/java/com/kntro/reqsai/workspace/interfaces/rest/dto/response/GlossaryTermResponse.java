package com.kntro.reqsai.workspace.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Glossary term resource")
public record GlossaryTermResponse(
        @Schema(description = "Unique identifier of the term") UUID id,
        @Schema(description = "Domain term name", example = "Sprint") String term,
        @Schema(description = "Plain-language definition of the term") String definition,
        @Schema(description = "Timestamp when the term was created") Instant createdAt,
        @Schema(description = "Timestamp of the last update") Instant updatedAt
) {}
