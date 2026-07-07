package com.kntro.reqsai.discovery.interfaces.rest.dto.request;

import com.kntro.reqsai.discovery.domain.model.Priority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

@Schema(description = "Request body to manually create a user story")
public record CreateUserStoryRequest(

        @Schema(description = "Short story title", example = "Bulk-import suppliers",
                minLength = 1, maxLength = 200, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 200)
        String title,

        @Schema(description = "Actor — \"as a …\"", example = "compliance analyst",
                minLength = 1, maxLength = 500, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 500)
        String role,

        @Schema(description = "Action — \"I want to …\"", example = "upload a CSV of suppliers",
                minLength = 1, maxLength = 500, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 500)
        String action,

        @Schema(description = "Benefit — \"so that …\"", example = "I avoid entering them one by one",
                minLength = 1, maxLength = 500, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 500)
        String benefit,

        @Schema(description = "Backlog priority", example = "HIGH", requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"LOW", "MEDIUM", "HIGH", "CRITICAL"})
        @NotNull
        Priority priority,

        @Schema(description = "Optional effort estimate in story points", example = "5",
                minimum = "0", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        @PositiveOrZero
        @Nullable Integer storyPoints
) {
}
