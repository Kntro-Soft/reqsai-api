package com.kntro.reqsai.workspace.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import java.util.List;

@Schema(description = "Request body to update a project details")
public record UpdateProjectRequest(
        @Schema(description = "Project name", minLength = 1, maxLength = 150, example = "Updated Project Name")
        @NotBlank @Size(max = 150)
        String name,

        @Schema(description = "Project description", maxLength = 2000, nullable = true, example = "Updated description")
        @Size(max = 2000)
        @Nullable String description,

        @Schema(description = "List of programming languages used", example = "[\"Java\", \"Kotlin\"]")
        @NotEmpty List<String> programmingLanguages,

        @Schema(description = "List of frameworks used", example = "[\"Spring Boot\"]")
        @NotEmpty List<String> frameworks,

        @Schema(description = "List of client platforms", example = "[\"Web\", \"Mobile\"]")
        @NotEmpty List<String> clientPlatforms,

        @Schema(description = "List of database systems", example = "[\"PostgreSQL\"]")
        @NotEmpty List<String> databases,

        @Schema(description = "Architectural style", example = "Clean Architecture")
        @NotBlank String architecture,

        @Schema(description = "Business Domain", example = "E-commerce")
        @NotBlank String domain
) {}
