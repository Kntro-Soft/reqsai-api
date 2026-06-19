package com.kntro.reqsai.workspace.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Schema(description = "Technical stack description of the project")
public record TechnicalProfileDto(
        @Schema(description = "List of programming languages used", example = "[\"Java\", \"TypeScript\"]")
        @NotEmpty List<String> programmingLanguages,

        @Schema(description = "List of frameworks used", example = "[\"Spring Boot\", \"Next.js\"]")
        @NotEmpty List<String> frameworks,

        @Schema(description = "List of client platforms", example = "[\"Web\", \"Mobile\"]")
        @NotEmpty List<String> clientPlatforms,

        @Schema(description = "List of database systems", example = "[\"PostgreSQL\", \"Redis\"]")
        @NotEmpty List<String> databases,

        @Schema(description = "Architectural style", example = "Clean Architecture")
        @NotBlank String architecture,

        @Schema(description = "Business Domain", example = "Fintech")
        @NotBlank String domain
) {}
