package com.kntro.reqsai.workspace.interfaces.rest.swagger;

import com.kntro.reqsai.shared.interfaces.pagination.PageResponse;
import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseBadRequest;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseNotFound;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.CreateProjectRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.ProjectResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.UpdateProjectRequest;
import java.util.UUID;

@RequestMapping(path = ApiVersioning.BASE + "/organizations/{orgId}/projects", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Projects", description = "Workspace project foundation operations")
public interface ProjectController {

    @Operation(summary = "Create a project manually", description = "Creates a new project within the organization. Validates plan limits and name uniqueness. Triggers automatic glossary provisioning.")
    @ApiResponse(responseCode = "201", description = "Project created successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProjectResponse.class)))
    @ApiResponseBadRequest
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(version = ApiVersioning.V1)
    ResponseEntity<ProjectResponse> create(
            @Parameter(description = "Organization context UUID") @PathVariable UUID orgId,
            @Valid @RequestBody CreateProjectRequest request,
            Authentication authentication
    );

    @Operation(summary = "Update a project manually", description = "Updates the project details and stack. Verifies organization context and checks name uniqueness.")
    @ApiResponse(responseCode = "200", description = "Project updated successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProjectResponse.class)))
    @ApiResponseBadRequest
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PutMapping(value = "/{projectId}", version = ApiVersioning.V1)
    ResponseEntity<ProjectResponse> update(
            @Parameter(description = "Organization context UUID") @PathVariable UUID orgId,
            @Parameter(description = "Project UUID") @PathVariable UUID projectId,
            @Valid @RequestBody UpdateProjectRequest request,
            Authentication authentication
    );

    @Operation(summary = "Get a project by id", description = "Returns a single project belonging to the given organization and authenticated tenant.")
    @ApiResponse(responseCode = "200", description = "Project found",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProjectResponse.class)))
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(value = "/{projectId}", version = ApiVersioning.V1)
    ResponseEntity<ProjectResponse> getById(
            @Parameter(description = "Organization context UUID") @PathVariable UUID orgId,
            @Parameter(description = "Project UUID") @PathVariable UUID projectId,
            Authentication authentication
    );

    @Operation(summary = "List projects for an organization", description = "Returns a paginated list of projects for the given organization, scoped to the authenticated tenant.")
    @ApiResponse(responseCode = "200", description = "Paginated project list",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(version = ApiVersioning.V1)
    ResponseEntity<PageResponse<ProjectResponse>> list(
            @Parameter(description = "Organization context UUID") @PathVariable UUID orgId,
            @Parameter(description = "Zero-based page index", example = "0") @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size (max 100)", example = "20") @RequestParam(required = false) Integer size,
            @Parameter(description = "Sort field: createdAt | updatedAt | name | status", example = "createdAt") @RequestParam(required = false) String sortBy,
            @Parameter(description = "Sort direction: ASC | DESC", example = "DESC") @RequestParam(required = false) String sortDirection,
            Authentication authentication
    );

    @Operation(summary = "Archive a project manually", description = "Archives the project under the organization. Archived projects are hidden from the default workspace queries.")
    @ApiResponse(responseCode = "204", description = "Project archived successfully")
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(value = "/{projectId}/archive", version = ApiVersioning.V1)
    ResponseEntity<Void> archive(
            @Parameter(description = "Organization context UUID") @PathVariable UUID orgId,
            @Parameter(description = "Project UUID") @PathVariable UUID projectId,
            Authentication authentication
    );

    @Operation(summary = "Restore an archived project", description = "Restores an archived project back to the active workspace list.")
    @ApiResponse(responseCode = "204", description = "Project restored successfully")
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(value = "/{projectId}/restore", version = ApiVersioning.V1)
    ResponseEntity<Void> restore(
            @Parameter(description = "Organization context UUID") @PathVariable UUID orgId,
            @Parameter(description = "Project UUID") @PathVariable UUID projectId,
            Authentication authentication
    );

    @Operation(summary = "Delete a project permanently", description = "Permanently deletes the project and its tenant-scoped dependent data under the organization.")
    @ApiResponse(responseCode = "204", description = "Project deleted permanently")
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @DeleteMapping(value = "/{projectId}", version = ApiVersioning.V1)
    ResponseEntity<Void> delete(
            @Parameter(description = "Organization context UUID") @PathVariable UUID orgId,
            @Parameter(description = "Project UUID") @PathVariable UUID projectId,
            Authentication authentication
    );
}
