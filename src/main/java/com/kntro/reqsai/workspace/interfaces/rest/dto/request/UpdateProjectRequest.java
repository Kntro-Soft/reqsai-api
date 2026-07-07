package com.kntro.reqsai.workspace.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Request body to update a project. Only {@code name} is required; the technical profile is optional
 * context (a missing list clears it to empty, a missing architecture/domain clears it to unset).
 */
@Schema(description = "Request body to update a project details")
public record UpdateProjectRequest(
        @Schema(description = "Project name", minLength = 1, maxLength = 150, example = "Updated Project Name")
        @NotBlank @Size(max = 150)
        String name,

        @Schema(description = "Project description", maxLength = 2000, nullable = true, example = "Updated description")
        @Size(max = 2000)
        @Nullable String description,

        @Schema(description = "List of programming languages used", nullable = true, example = "[\"Java\", \"Kotlin\"]")
        @Nullable List<String> programmingLanguages,

        @Schema(description = "List of frameworks used", nullable = true, example = "[\"Spring Boot\"]")
        @Nullable List<String> frameworks,

        @Schema(description = "List of client platforms", nullable = true, example = "[\"Web\", \"Mobile\"]")
        @Nullable List<String> clientPlatforms,

        @Schema(description = "List of database systems", nullable = true, example = "[\"PostgreSQL\"]")
        @Nullable List<String> databases,

        @Schema(description = "Architectural style", nullable = true, example = "Clean Architecture")
        @Size(max = 100)
        @Nullable String architecture,

        @Schema(description = "Business Domain", nullable = true, example = "E-commerce")
        @Size(max = 100)
        @Nullable String domain
) {}
