package com.kntro.reqsai.discovery.interfaces.rest.swagger;

import com.kntro.reqsai.discovery.interfaces.rest.dto.request.CreateUserStoryRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.UserStoryResponse;
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
 * API contract for project-scoped user story endpoints; implemented by {@code controllers.ProjectStoryControllerImpl}.
 * All routes are tenant-scoped — the active schema is resolved from the JWT {@code orgId}.
 */
@RequestMapping(path = ApiVersioning.BASE + "/projects/{projectId}/stories", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "User Stories", description = "Backlog user stories — manual creation and project-level backlog")
public interface ProjectStoryController {

    @Operation(
            summary = "Create a user story (manual)",
            description = """
                    Manually creates a user story under the given project, in **DRAFT** status, scoped to \
                    the authenticated user's tenant. For teams that already have a backlog and want to \
                    upload stories directly (no discovery session involved).""")
    @ApiResponse(
            responseCode = "201",
            description = "Story created — `Location` header points to the new resource",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = UserStoryResponse.class),
                    examples = @ExampleObject(value = """
                            {
                              "id": "019756a0-1234-7abc-8def-000000000010",
                              "projectId": "019756a0-1234-7abc-8def-000000000002",
                              "sessionId": null,
                              "title": "Bulk-import suppliers",
                              "role": "compliance analyst",
                              "action": "upload a CSV of suppliers",
                              "benefit": "I avoid entering them one by one",
                              "priority": "HIGH",
                              "storyPoints": 5,
                              "status": "DRAFT",
                              "createdAt": "2026-06-15T13:55:00Z",
                              "updatedAt": "2026-06-15T13:55:00Z"
                            }""")))
    @ApiResponseBadRequest
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(version = ApiVersioning.V1)
    ResponseEntity<UserStoryResponse> create(
            @Parameter(description = "Project to create the story under", required = true, example = "019756a0-1234-7abc-8def-000000000002")
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateUserStoryRequest request);
}
