package com.kntro.reqsai.workspace.interfaces.rest.swagger;

import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseBadRequest;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseNotFound;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.AddProjectConstraintRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.UpdateProjectConstraintRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.ProjectConstraintResponse;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.UUID;

@RequestMapping(
        path = ApiVersioning.BASE + "/organizations/{orgId}/projects/{projectId}/constraints",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Project Constraints", description = "Project context constraints maintenance operations")
public interface ProjectConstraintController {

    @Operation(
            summary = "Add a project constraint manually",
            description = """
                    Creates a new project constraint under the given active project.

                    - Constraints enrich the project's context for future discovery and RAG workflows
                    - Duplicate descriptions are rejected ignoring leading/trailing spaces and case""")
    @ApiResponse(
            responseCode = "201",
            description = "Project constraint created successfully",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ProjectConstraintResponse.class),
                    examples = @ExampleObject(value = """
                            {
                              "id": "019756a0-1234-7abc-8def-000000000401",
                              "description": "Debe integrarse con SAP",
                              "createdAt": "2026-06-20T13:30:00Z",
                              "updatedAt": "2026-06-20T13:30:00Z"
                            }""")))
    @ApiResponseBadRequest
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(version = ApiVersioning.V1)
    ResponseEntity<ProjectConstraintResponse> addConstraint(
            @Parameter(description = "Organization context UUID") @PathVariable UUID orgId,
            @Parameter(description = "Project UUID") @PathVariable UUID projectId,
            @Valid @RequestBody AddProjectConstraintRequest request,
            Authentication authentication);

    @Operation(summary = "List project constraints", description = "Returns the constraints currently stored for the project.")
    @ApiResponse(responseCode = "200", description = "Project constraints retrieved successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProjectConstraintResponse.class)))
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(version = ApiVersioning.V1)
    ResponseEntity<List<ProjectConstraintResponse>> listConstraints(
            @Parameter(description = "Organization context UUID") @PathVariable UUID orgId,
            @Parameter(description = "Project UUID") @PathVariable UUID projectId,
            Authentication authentication);

    @Operation(summary = "Get a project constraint", description = "Returns one project constraint from the project.")
    @ApiResponse(responseCode = "200", description = "Project constraint retrieved successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProjectConstraintResponse.class)))
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(value = "/{constraintId}", version = ApiVersioning.V1)
    ResponseEntity<ProjectConstraintResponse> getConstraint(
            @Parameter(description = "Organization context UUID") @PathVariable UUID orgId,
            @Parameter(description = "Project UUID") @PathVariable UUID projectId,
            @Parameter(description = "Project constraint UUID") @PathVariable UUID constraintId,
            Authentication authentication);

    @Operation(summary = "Replace a project constraint", description = "Fully replaces the description of one project constraint.")
    @ApiResponse(responseCode = "200", description = "Project constraint updated successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProjectConstraintResponse.class)))
    @ApiResponseBadRequest
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PutMapping(value = "/{constraintId}", version = ApiVersioning.V1)
    ResponseEntity<ProjectConstraintResponse> updateConstraint(
            @Parameter(description = "Organization context UUID") @PathVariable UUID orgId,
            @Parameter(description = "Project UUID") @PathVariable UUID projectId,
            @Parameter(description = "Project constraint UUID") @PathVariable UUID constraintId,
            @Valid @RequestBody UpdateProjectConstraintRequest request,
            Authentication authentication);

    @Operation(summary = "Delete a project constraint", description = "Removes one project constraint from the project.")
    @ApiResponse(responseCode = "204", description = "Project constraint deleted successfully")
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @DeleteMapping(value = "/{constraintId}", version = ApiVersioning.V1)
    ResponseEntity<Void> deleteConstraint(
            @Parameter(description = "Organization context UUID") @PathVariable UUID orgId,
            @Parameter(description = "Project UUID") @PathVariable UUID projectId,
            @Parameter(description = "Project constraint UUID") @PathVariable UUID constraintId,
            Authentication authentication);
}
