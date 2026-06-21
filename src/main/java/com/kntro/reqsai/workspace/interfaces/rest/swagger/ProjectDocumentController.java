package com.kntro.reqsai.workspace.interfaces.rest.swagger;

import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseBadRequest;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseNotFound;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.CreateProjectDocumentRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.UpdateProjectDocumentRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.ProjectDocumentResponse;
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
        path = ApiVersioning.BASE + "/organizations/{orgId}/projects/{projectId}/documents",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Project Documents", description = "Project document metadata maintenance operations")
public interface ProjectDocumentController {

    @Operation(
            summary = "Create a project document metadata record",
            description = """
                    Creates a new metadata-only project document under the given active project.

                    - This v1 does not upload or store a file yet
                    - Duplicate names are rejected ignoring leading/trailing spaces and case
                    - The organization's plan limit for project documents is enforced""")
    @ApiResponse(
            responseCode = "201",
            description = "Project document created successfully",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ProjectDocumentResponse.class),
                    examples = @ExampleObject(value = """
                            {
                              "id": "019756a0-1234-7abc-8def-000000000501",
                              "projectId": "019756a0-1234-7abc-8def-000000000201",
                              "name": "Business Rules v1",
                              "documentType": "BUSINESS_RULES",
                              "status": "ACTIVE",
                              "createdAt": "2026-06-20T13:30:00Z",
                              "updatedAt": "2026-06-20T13:30:00Z"
                            }""")))
    @ApiResponseBadRequest
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(version = ApiVersioning.V1)
    ResponseEntity<ProjectDocumentResponse> createDocument(
            @Parameter(description = "Organization context UUID") @PathVariable UUID orgId,
            @Parameter(description = "Project UUID") @PathVariable UUID projectId,
            @Valid @RequestBody CreateProjectDocumentRequest request,
            Authentication authentication);

    @Operation(summary = "List project document metadata", description = "Returns the active document metadata currently stored for the project.")
    @ApiResponse(responseCode = "200", description = "Project documents retrieved successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProjectDocumentResponse.class)))
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(version = ApiVersioning.V1)
    ResponseEntity<List<ProjectDocumentResponse>> listDocuments(
            @Parameter(description = "Organization context UUID") @PathVariable UUID orgId,
            @Parameter(description = "Project UUID") @PathVariable UUID projectId,
            Authentication authentication);

    @Operation(summary = "Get project document metadata", description = "Returns one active project document metadata record.")
    @ApiResponse(responseCode = "200", description = "Project document retrieved successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProjectDocumentResponse.class)))
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(value = "/{documentId}", version = ApiVersioning.V1)
    ResponseEntity<ProjectDocumentResponse> getDocument(
            @Parameter(description = "Organization context UUID") @PathVariable UUID orgId,
            @Parameter(description = "Project UUID") @PathVariable UUID projectId,
            @Parameter(description = "Project document UUID") @PathVariable UUID documentId,
            Authentication authentication);

    @Operation(summary = "Replace project document metadata", description = "Fully replaces the name and document type of one active project document.")
    @ApiResponse(responseCode = "200", description = "Project document updated successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProjectDocumentResponse.class)))
    @ApiResponseBadRequest
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PutMapping(value = "/{documentId}", version = ApiVersioning.V1)
    ResponseEntity<ProjectDocumentResponse> updateDocument(
            @Parameter(description = "Organization context UUID") @PathVariable UUID orgId,
            @Parameter(description = "Project UUID") @PathVariable UUID projectId,
            @Parameter(description = "Project document UUID") @PathVariable UUID documentId,
            @Valid @RequestBody UpdateProjectDocumentRequest request,
            Authentication authentication);

    @Operation(summary = "Delete project document metadata", description = "Permanently deletes one active project document metadata record.")
    @ApiResponse(responseCode = "204", description = "Project document deleted successfully")
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @DeleteMapping(value = "/{documentId}", version = ApiVersioning.V1)
    ResponseEntity<Void> deleteDocument(
            @Parameter(description = "Organization context UUID") @PathVariable UUID orgId,
            @Parameter(description = "Project UUID") @PathVariable UUID projectId,
            @Parameter(description = "Project document UUID") @PathVariable UUID documentId,
            Authentication authentication);
}
