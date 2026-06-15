package com.kntro.reqsai.discovery.interfaces.rest.swagger;

import com.kntro.reqsai.discovery.interfaces.rest.dto.request.CreateDiscoverySessionRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.DiscoverySessionResponse;
import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseBadRequest;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

/**
 * API contract for discovery-session endpoints; implemented by {@code controllers.DiscoverySessionControllerImpl}.
 * All routes are tenant-scoped — the active schema is resolved from the JWT {@code orgId}.
 */
@RequestMapping(path = ApiVersioning.BASE + "/projects/{projectId}/sessions", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Discovery Sessions", description = "Requirements-elicitation sessions — lifecycle from DRAFT to COMPLETED")
public interface DiscoverySessionController {

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
}
