package com.kntro.reqsai.discovery.interfaces.rest.swagger;

import com.kntro.reqsai.discovery.interfaces.rest.dto.request.AcceptSuggestionRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.SuggestionResponse;
import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseNotFound;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.UUID;

/**
 * API contract for session-scoped suggestion endpoints; implemented by
 * {@code controllers.SessionSuggestionControllerImpl}.
 *
 * <p>Suggestions represent AI-generated proposals that have NOT yet been committed to the backlog.
 * The analyst reviews each one and either accepts (optionally editing the draft) or dismisses it.
 */
@RequestMapping(path = ApiVersioning.BASE + "/sessions/{sessionId}/suggestions",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Suggestions",
        description = "AI-generated proposals pending analyst review before backlog commit")
public interface SessionSuggestionController {

    @Operation(
            summary = "List pending suggestions",
            description = "Returns all PENDING suggestions for the session. Accepted and dismissed suggestions are excluded.")
    @ApiResponse(responseCode = "200",
            description = "List of pending suggestions (may be empty)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(type = "array", implementation = SuggestionResponse.class)))
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping
    ResponseEntity<List<SuggestionResponse>> listPending(
            @Parameter(description = "Session identifier", required = true)
            @PathVariable UUID sessionId);

    @Operation(
            summary = "Accept a suggestion",
            description = """
                    Accepts a PENDING suggestion and commits the corresponding change to the backlog:
                    - **NEW_STORY** → creates a new user story from the draft fields.
                    - **UPDATE_STORY** → updates the target story's fields.
                    - **EDGE_CASE** → adds an acceptance criterion to the target story.
                    - **CLARIFYING_QUESTION** → marks accepted, no backlog change.

                    All `edited*` fields in the request body are optional overrides.
                    Omit the body (or pass `{}`) to use the draft as-is.""")
    @ApiResponse(responseCode = "200",
            description = "Suggestion accepted",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = SuggestionResponse.class)))
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(path = "/{suggestionId}/accept", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<SuggestionResponse> accept(
            @Parameter(description = "Session identifier", required = true) @PathVariable UUID sessionId,
            @Parameter(description = "Suggestion identifier", required = true) @PathVariable UUID suggestionId,
            @RequestBody(required = false) AcceptSuggestionRequest request);

    @Operation(
            summary = "Dismiss a suggestion",
            description = "Dismisses a PENDING suggestion without taking any backlog action.")
    @ApiResponse(responseCode = "200",
            description = "Suggestion dismissed",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = SuggestionResponse.class)))
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping("/{suggestionId}/dismiss")
    ResponseEntity<SuggestionResponse> dismiss(
            @Parameter(description = "Session identifier", required = true) @PathVariable UUID sessionId,
            @Parameter(description = "Suggestion identifier", required = true) @PathVariable UUID suggestionId);
}
