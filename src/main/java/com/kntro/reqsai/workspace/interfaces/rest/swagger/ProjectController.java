package com.kntro.reqsai.workspace.interfaces.rest.swagger;

import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseBadRequest;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseNotFound;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.CreateProjectRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.ProjectResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
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
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.UpdateProjectRequest;
import java.util.List;
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

    @Operation(summary = "List projects", description = "Returns every project in the organization (tenant-scoped), newest first.")
    @ApiResponse(responseCode = "200", description = "Projects in the organization",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = ProjectResponse.class))))
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(version = ApiVersioning.V1)
    ResponseEntity<List<ProjectResponse>> list(
            @Parameter(description = "Organization context UUID") @PathVariable UUID orgId,
            Authentication authentication
    );

    @Operation(summary = "Get a project", description = "Returns a single project that belongs to the organization.")
    @ApiResponse(responseCode = "200", description = "Project found",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProjectResponse.class)))
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(value = "/{projectId}", version = ApiVersioning.V1)
    ResponseEntity<ProjectResponse> get(
            @Parameter(description = "Organization context UUID") @PathVariable UUID orgId,
            @Parameter(description = "Project UUID") @PathVariable UUID projectId,
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

    @Operation(summary = "Delete a project manually", description = "Permanently deletes the project and all cascaded data (glossary, etc.) under the organization.")
    @ApiResponse(responseCode = "204", description = "Project deleted successfully")
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
