package com.kntro.reqsai.workspace.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Request body to create a project. Only {@code name} is required: the technical profile is optional
 * context the UI collects under "advanced". A missing list is treated as empty; a missing
 * architecture/domain is treated as unset.
 */
@Schema(description = "Request body to create a project")
public record CreateProjectRequest(
        @Schema(description = "Project name", minLength = 1, maxLength = 150, example = "My Awesome Project")
        @NotBlank @Size(max = 150)
        String name,

        @Schema(description = "Project description", maxLength = 2000, nullable = true, example = "A web application for requirement elicitation and management")
        @Size(max = 2000)
        @Nullable String description,

        @Schema(description = "List of programming languages used", nullable = true, example = "[\"Java\", \"TypeScript\"]")
        @Nullable List<String> programmingLanguages,

        @Schema(description = "List of frameworks used", nullable = true, example = "[\"Spring Boot\", \"Next.js\"]")
        @Nullable List<String> frameworks,

        @Schema(description = "List of client platforms", nullable = true, example = "[\"Web\", \"Mobile\"]")
        @Nullable List<String> clientPlatforms,

        @Schema(description = "List of database systems", nullable = true, example = "[\"PostgreSQL\", \"Redis\"]")
        @Nullable List<String> databases,

        @Schema(description = "Architectural style", nullable = true, example = "Clean Architecture")
        @Size(max = 100)
        @Nullable String architecture,

        @Schema(description = "Business Domain", nullable = true, example = "Fintech")
        @Size(max = 100)
        @Nullable String domain
) {}
