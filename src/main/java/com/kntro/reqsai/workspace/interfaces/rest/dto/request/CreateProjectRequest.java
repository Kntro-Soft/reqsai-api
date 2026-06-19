package com.kntro.reqsai.workspace.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import java.util.List;

@Schema(description = "Request body to create a project")
public record CreateProjectRequest(
        @Schema(description = "Project name", minLength = 1, maxLength = 150, example = "My Awesome Project")
        @NotBlank @Size(max = 150)
        String name,

        @Schema(description = "Project description", maxLength = 2000, nullable = true, example = "A web application for requirement elicitation and management")
        @Size(max = 2000)
        @Nullable String description,

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
