package com.kntro.reqsai.discovery.interfaces.rest.swagger;

import com.kntro.reqsai.discovery.interfaces.rest.dto.response.StorySourcesResponse;
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
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

/**
 * API contract for the meeting fragments a story comes from; implemented by
 * {@code controllers.StorySourceControllerImpl}. Tenant-scoped: the schema is resolved from the JWT {@code orgId}.
 */
@RequestMapping(path = ApiVersioning.BASE + "/projects/{projectId}/stories/{storyId}/sources",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Story sources", description = "Where a user story comes from in the meetings")
public interface StorySourceController {

    @Operation(
            summary = "See where a story comes from in the transcript",
            description = """
                    Returns the fragments of the meetings the story was generated or accepted from, each with \
                    the session it was said in, the moment of the meeting (offset from the start of the \
                    recording and wall-clock instant), the transcript text and the speaker when known. \
                    The `origin` field says what to show: `SESSION` (fragments follow), `UNAVAILABLE` (the \
                    story was generated in a session but, being from before fragments were stored, no \
                    fragment is kept; the session is still reported) or `MANUAL` (the story does not come \
                    from a session). None of them is an error. Requires the STORY_READ project permission.""")
    @ApiResponse(
            responseCode = "200",
            description = "Sources of the story",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = StorySourcesResponse.class),
                    examples = @ExampleObject(value = """
                            {
                              "storyId": "019756a0-1234-7abc-8def-000000000010",
                              "origin": "SESSION",
                              "sessionId": null,
                              "sessionTitle": null,
                              "sessionStartedAt": null,
                              "fragments": [
                                {
                                  "id": "019756a0-1234-7abc-8def-000000000020",
                                  "sessionId": "019756a0-1234-7abc-8def-000000000001",
                                  "sessionTitle": "Kickoff con el restaurante",
                                  "sessionStartedAt": "2026-11-10T15:00:00Z",
                                  "quote": "Necesito que el comensal pueda reservar una mesa en línea",
                                  "transcriptText": "Necesito que el comensal pueda reservar una mesa en línea.",
                                  "startSequence": 12,
                                  "endSequence": 12,
                                  "startMs": 754000,
                                  "endMs": 761500,
                                  "occurredAt": "2026-11-10T15:12:34Z",
                                  "speakerName": "Ana Torres"
                                }
                              ]
                            }""")))
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(version = ApiVersioning.V1)
    ResponseEntity<StorySourcesResponse> getSources(
            @Parameter(description = "Project the story belongs to", required = true)
            @PathVariable UUID projectId,
            @Parameter(description = "Story whose sources to show", required = true)
            @PathVariable UUID storyId);
}
