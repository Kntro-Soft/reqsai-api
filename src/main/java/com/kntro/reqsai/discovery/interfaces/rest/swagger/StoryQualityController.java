package com.kntro.reqsai.discovery.interfaces.rest.swagger;

import com.kntro.reqsai.discovery.interfaces.rest.dto.response.StoryQualityResponse;
import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseNotFound;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

/**
 * API contract for the quality assessment of a story; implemented by {@code controllers.StoryQualityControllerImpl}.
 * Tenant-scoped — the active schema is resolved from the JWT {@code orgId}.
 */
@RequestMapping(
        path = ApiVersioning.BASE + "/projects/{projectId}/stories/{storyId}/quality",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Story Quality", description = "Quality score of a user story and the characteristics it does not meet yet")
public interface StoryQualityController {

    @Operation(
            summary = "Evaluate the quality of a story",
            description = """
                    Evaluates the story as it is now against the ISO/IEC/IEEE 29148 requirement quality \
                    characteristics and the Quality User Story framework: **ambiguity**, **atomicity**, \
                    **verifiability** and **completeness**. Returns an overall score from 0 to 100 and, per \
                    characteristic, its score, what was observed and how to fix it. The result is stored as the \
                    story's latest assessment; calling it again re-evaluates and replaces it. The evaluation is \
                    rule-based and deterministic: it does not call any AI provider and uses no AI quota. \
                    Requires STORY_WRITE.""")
    @ApiResponse(
            responseCode = "200",
            description = "Story evaluated; the body is the stored assessment",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = StoryQualityResponse.class),
                    examples = @ExampleObject(value = """
                            {
                              "storyId": "019756a0-1234-7abc-8def-000000000010",
                              "score": 63,
                              "characteristics": [
                                {
                                  "characteristic": "AMBIGUITY",
                                  "score": 75,
                                  "met": false,
                                  "observation": "Se detectaron términos ambiguos: «rápido».",
                                  "suggestion": "Reemplaza cada término por un valor medible o una lista cerrada."
                                },
                                {
                                  "characteristic": "ATOMICITY",
                                  "score": 100,
                                  "met": true,
                                  "observation": "La historia describe una única acción de tamaño manejable.",
                                  "suggestion": null
                                },
                                {
                                  "characteristic": "VERIFIABILITY",
                                  "score": 0,
                                  "met": false,
                                  "observation": "La historia no tiene criterios de aceptación, así que no hay cómo comprobar que se cumple.",
                                  "suggestion": "Agrega al menos un criterio Dado/Cuando/Entonces con un resultado observable."
                                },
                                {
                                  "characteristic": "COMPLETENESS",
                                  "score": 75,
                                  "met": false,
                                  "observation": "No tiene criterios de aceptación.",
                                  "suggestion": "Agrega los criterios de aceptación que la enmarcan."
                                }
                              ],
                              "evaluatedAt": "2026-11-18T12:00:00Z"
                            }""")))
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(version = ApiVersioning.V1)
    ResponseEntity<StoryQualityResponse> evaluate(
            @Parameter(description = "Project the story belongs to", required = true)
            @PathVariable UUID projectId,
            @Parameter(description = "Story to evaluate", required = true)
            @PathVariable UUID storyId);

    @Operation(
            summary = "Get the latest quality assessment of a story",
            description = """
                    Returns the story's latest stored assessment without evaluating again. A story that has \
                    never been evaluated answers 404 with code STORY_QUALITY_NOT_EVALUATED. The assessment is \
                    refreshed on its own whenever an already-assessed story or one of its acceptance criteria \
                    is edited. Requires STORY_READ.""")
    @ApiResponse(
            responseCode = "200",
            description = "Latest assessment",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = StoryQualityResponse.class)))
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(version = ApiVersioning.V1)
    ResponseEntity<StoryQualityResponse> get(
            @Parameter(description = "Project the story belongs to", required = true)
            @PathVariable UUID projectId,
            @Parameter(description = "Story whose assessment to read", required = true)
            @PathVariable UUID storyId);
}
