package com.kntro.reqsai.discovery.interfaces.rest.swagger;

import com.kntro.reqsai.discovery.interfaces.rest.dto.request.ApplyStoryImprovementRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.ApplyStoryImprovementResponse;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.StoryImprovementResponse;
import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseBadRequest;
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
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

/**
 * API contract to improve a story with AI; implemented by {@code controllers.StoryImprovementControllerImpl}.
 * Tenant-scoped — the active schema is resolved from the JWT {@code orgId}.
 */
@RequestMapping(
        path = ApiVersioning.BASE + "/projects/{projectId}/stories/{storyId}/quality/improvement",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Story Improvement", description = "Propose an AI-improved version of a story and apply it once reviewed")
public interface StoryImprovementController {

    @Operation(
            summary = "Propose an improved version of a story",
            description = """
                    Asks the AI for an improved version of the story: it gets the story, the quality \
                    characteristics it does not meet, the project glossary and constraints and the meeting \
                    fragments the story comes from, and fixes ambiguity, atomicity, verifiability and \
                    completeness without changing what the story is about. The AI never invents a number, limit, \
                    name or rule the sources do not state: where a measurable value is needed and unknown, the \
                    text holds a `[[...]]` token and `pendingValues` carries a question for the analyst. \
                    **Nothing is stored**: the story is unchanged until the proposal is applied. \
                    `expectedQuality` is the rule-based evaluation of the proposal as it is (not stored, no AI). \
                    The proposal is written in the language of the story. Needs the monthly AI allowance and is \
                    metered as `STORY_IMPROVEMENT`. Requires STORY_WRITE.""")
    @ApiResponse(
            responseCode = "200",
            description = "Proposal; the story is unchanged",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = StoryImprovementResponse.class),
                    examples = @ExampleObject(value = """
                            {
                              "storyId": "019756a0-1234-7abc-8def-000000000010",
                              "proposal": {
                                "title": "Exportar el backlog a CSV",
                                "role": "analista de cumplimiento",
                                "action": "exportar el backlog aprobado a un archivo CSV",
                                "benefit": "compartirlo con el cliente sin reescribirlo",
                                "acceptanceCriteria": [
                                  {
                                    "scenario": "Exportación correcta",
                                    "given": "hay historias aprobadas en el backlog",
                                    "when": "el analista exporta el backlog",
                                    "then": "el sistema descarga un archivo CSV en menos de [[tiempo máximo de respuesta]] segundos"
                                  }
                                ]
                              },
                              "pendingValues": [
                                {
                                  "token": "[[tiempo máximo de respuesta]]",
                                  "hint": "¿En cuántos segundos debe responder?"
                                }
                              ],
                              "fixes": ["Se reemplazó «rápido» por un valor pendiente"],
                              "expectedQuality": {
                                "storyId": "019756a0-1234-7abc-8def-000000000010",
                                "score": 100,
                                "characteristics": [],
                                "evaluatedAt": "2026-11-18T12:00:00Z"
                              }
                            }""")))
    @ApiResponse(responseCode = "402",
            description = "The organization's monthly AI allowance is spent (AI_QUOTA_EXCEEDED)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422",
            description = "The AI is not available, or its reply could not be used (REQUIREMENT_GENERATION_FAILED)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(version = ApiVersioning.V1)
    ResponseEntity<StoryImprovementResponse> propose(
            @Parameter(description = "Project the story belongs to", required = true)
            @PathVariable UUID projectId,
            @Parameter(description = "Story to improve", required = true)
            @PathVariable UUID storyId);

    @Operation(
            summary = "Apply an improved version of a story",
            description = """
                    Applies the proposal the analyst reviewed (and may have edited): in one transaction it \
                    replaces the title, role, action and benefit and **replaces** the acceptance criteria with \
                    those of the body, then evaluates the quality again and stores it. Like a manual edit, it \
                    keeps the priority, the estimate and the status of the story (an approved story stays \
                    approved) and does not touch the text the AI first generated, so the change counts as a \
                    rewrite in the quality indicators. It makes no AI call. If any `[[...]]` token is still in \
                    any text it answers 422 IMPROVEMENT_PENDING_VALUES and changes nothing. Requires STORY_WRITE.""")
    @ApiResponse(
            responseCode = "200",
            description = "The updated story and its stored quality",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApplyStoryImprovementResponse.class)))
    @ApiResponse(responseCode = "422",
            description = "A [[...]] pending value is still in the text (IMPROVEMENT_PENDING_VALUES)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponseBadRequest
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(path = "/apply", version = ApiVersioning.V1)
    ResponseEntity<ApplyStoryImprovementResponse> apply(
            @Parameter(description = "Project the story belongs to", required = true)
            @PathVariable UUID projectId,
            @Parameter(description = "Story to update", required = true)
            @PathVariable UUID storyId,
            @Valid @RequestBody ApplyStoryImprovementRequest request);
}
