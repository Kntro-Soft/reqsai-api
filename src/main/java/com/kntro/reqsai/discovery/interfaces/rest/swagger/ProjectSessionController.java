package com.kntro.reqsai.discovery.interfaces.rest.swagger;

import com.kntro.reqsai.discovery.interfaces.rest.dto.request.CreateDiscoverySessionRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.DiscoverySessionResponse;
import com.kntro.reqsai.shared.interfaces.pagination.PageResponse;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * API contract for project-scoped discovery-session endpoints; implemented by
 * {@code controllers.ProjectSessionControllerImpl}. All routes are tenant-scoped — the active schema
 * is resolved from the JWT {@code orgId}.
 */
@RequestMapping(path = ApiVersioning.BASE + "/projects/{projectId}/sessions", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Discovery Sessions", description = "Requirements-elicitation sessions — lifecycle from DRAFT to COMPLETED")
public interface ProjectSessionController {

    @Operation(
            summary = "Create a discovery session",
            description = """
                    Creates a new requirements-elicitation session in **DRAFT** status under the given project, \
                    scoped to the authenticated user's tenant (resolved from the JWT `orgId`).

                    The session starts with no transcript and no recording. Use `POST /{id}/start` to begin \
                    recording once the session is created.""")
    @ApiResponse(
            responseCode = "201",
            description = "Session created — `Location` header points to the new resource",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = DiscoverySessionResponse.class),
                    examples = @ExampleObject(value = """
                            {
                              "id": "019756a0-1234-7abc-8def-000000000001",
                              "projectId": "019756a0-1234-7abc-8def-000000000002",
                              "title": "Sprint 24 — Requirements Elicitation",
                              "language": "es-PE",
                              "status": "DRAFT",
                              "startedAt": null,
                              "endedAt": null,
                              "audioDurationMs": 0,
                              "processingError": null,
                              "createdAt": "2026-06-15T13:55:00Z",
                              "updatedAt": "2026-06-15T13:55:00Z"
                            }""")))
    @ApiResponseBadRequest
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(version = ApiVersioning.V1)
    ResponseEntity<DiscoverySessionResponse> create(
            @Parameter(description = "Project to create the session under", required = true, example = "019756a0-1234-7abc-8def-000000000002")
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateDiscoverySessionRequest request);

    @Operation(
            summary = "Get a discovery session by id",
            description = "Returns a single discovery session belonging to the given project and the authenticated tenant.")
    @ApiResponse(
            responseCode = "200",
            description = "Session found",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = DiscoverySessionResponse.class)))
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(path = "/{sessionId}", version = ApiVersioning.V1)
    ResponseEntity<DiscoverySessionResponse> getById(
            @Parameter(description = "Project the session belongs to", required = true)
            @PathVariable UUID projectId,
            @Parameter(description = "Session identifier", required = true)
            @PathVariable UUID sessionId);

    @Operation(
            summary = "List discovery sessions for a project",
            description = "Returns a paginated list of discovery sessions for the given project, scoped to the authenticated tenant.")
    @ApiResponse(
            responseCode = "200",
            description = "Paginated list of sessions",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(version = ApiVersioning.V1)
    ResponseEntity<PageResponse<DiscoverySessionResponse>> list(
            @Parameter(description = "Project whose sessions to list", required = true)
            @PathVariable UUID projectId,
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size (max 100)", example = "20")
            @RequestParam(required = false) Integer size,
            @Parameter(description = "Sort field: createdAt | title | status", example = "createdAt")
            @RequestParam(required = false) String sortBy,
            @Parameter(description = "Sort direction: ASC | DESC", example = "DESC")
            @RequestParam(required = false) String sortDirection);

    @Operation(
            summary = "Start recording a discovery session",
            description = "Transitions a discovery session status from DRAFT to RECORDING to authorize streaming capture.")
    @ApiResponse(
            responseCode = "200",
            description = "Session started recording",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = DiscoverySessionResponse.class)))
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(path = "/{sessionId}/start", version = ApiVersioning.V1)
    ResponseEntity<DiscoverySessionResponse> start(
            @Parameter(description = "Project the session belongs to", required = true)
            @PathVariable UUID projectId,
            @Parameter(description = "Session identifier", required = true)
            @PathVariable UUID sessionId);

    @Operation(
            summary = "Pause a recording session",
            description = "Transitions a discovery session status from RECORDING to PAUSED.")
    @ApiResponse(
            responseCode = "200",
            description = "Session recording paused",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = DiscoverySessionResponse.class)))
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(path = "/{sessionId}/pause", version = ApiVersioning.V1)
    ResponseEntity<DiscoverySessionResponse> pause(
            @Parameter(description = "Project the session belongs to", required = true)
            @PathVariable UUID projectId,
            @Parameter(description = "Session identifier", required = true)
            @PathVariable UUID sessionId);

    @Operation(
            summary = "Resume a paused recording session",
            description = "Transitions a discovery session status from PAUSED to RECORDING.")
    @ApiResponse(
            responseCode = "200",
            description = "Session recording resumed",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = DiscoverySessionResponse.class)))
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(path = "/{sessionId}/resume", version = ApiVersioning.V1)
    ResponseEntity<DiscoverySessionResponse> resume(
            @Parameter(description = "Project the session belongs to", required = true)
            @PathVariable UUID projectId,
            @Parameter(description = "Session identifier", required = true)
            @PathVariable UUID sessionId);

    @Operation(
            summary = "Stop a recording session",
            description = "Transitions a discovery session status from RECORDING or PAUSED to STOPPED.")
    @ApiResponse(
            responseCode = "200",
            description = "Session recording stopped",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = DiscoverySessionResponse.class)))
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(path = "/{sessionId}/stop", version = ApiVersioning.V1)
    ResponseEntity<DiscoverySessionResponse> stop(
            @Parameter(description = "Project the session belongs to", required = true)
            @PathVariable UUID projectId,
            @Parameter(description = "Session identifier", required = true)
            @PathVariable UUID sessionId);
}
