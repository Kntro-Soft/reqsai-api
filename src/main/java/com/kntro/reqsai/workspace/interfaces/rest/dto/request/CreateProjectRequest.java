package com.kntro.reqsai.workspace.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

@Schema(description = "Request body to create a project")
public record CreateProjectRequest(
        @Schema(description = "Project name", minLength = 1, maxLength = 150, example = "My Awesome Project")
        @NotBlank @Size(max = 150)
        String name,

        @Schema(description = "Project description", maxLength = 2000, nullable = true, example = "A web application for requirement elicitation and management")
        @Size(max = 2000)
        @Nullable String description,

        @Schema(description = "Technical details profile")
        @NotNull @Valid
        TechnicalProfileDto technicalProfile
) {}
