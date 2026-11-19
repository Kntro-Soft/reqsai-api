package com.kntro.reqsai.discovery.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Schema(description = "The improved story to apply: the proposal the AI made, as the analyst reviewed and edited it. "
        + "Every [[...]] pending value must already be replaced; the acceptance criteria replace those of the story")
public record ApplyStoryImprovementRequest(

        @Schema(description = "Short story title", example = "Exportar el backlog a CSV",
                minLength = 1, maxLength = 200, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 200)
        String title,

        @Schema(description = "Actor", example = "analista de cumplimiento",
                minLength = 1, maxLength = 500, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 500)
        String role,

        @Schema(description = "Action", example = "exportar el backlog aprobado a un archivo CSV",
                minLength = 1, maxLength = 500, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 500)
        String action,

        @Schema(description = "Benefit", example = "compartirlo con el cliente sin reescribirlo",
                minLength = 1, maxLength = 500, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 500)
        String benefit,

        @Schema(description = "Acceptance criteria that replace those of the story (at most 20; empty removes them all)",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Size(max = 20)
        List<@Valid @NotNull Criterion> acceptanceCriteria
) {

    @Schema(description = "One Given / When / Then acceptance criterion")
    public record Criterion(

            @Schema(description = "Optional scenario label. Max 200 chars.", maxLength = 200,
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
            @Size(max = 200)
            @Nullable String scenario,

            @Schema(description = "Given: precondition or context. Max 1000 chars.",
                    example = "hay historias aprobadas en el backlog", minLength = 1, maxLength = 1000,
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 1000)
            String given,

            @Schema(description = "When: action or trigger. Max 1000 chars.",
                    example = "el analista exporta el backlog", minLength = 1, maxLength = 1000,
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 1000)
            String when,

            @Schema(description = "Then: expected, observable outcome. Max 1000 chars.",
                    example = "el sistema descarga un archivo CSV con todas las historias aprobadas",
                    minLength = 1, maxLength = 1000, requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 1000)
            String then
    ) {
    }
}
