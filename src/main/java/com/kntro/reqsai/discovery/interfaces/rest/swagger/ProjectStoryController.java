package com.kntro.reqsai.discovery.interfaces.rest.swagger;

import com.kntro.reqsai.discovery.interfaces.rest.dto.request.CreateUserStoryRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.request.UpdateUserStoryRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.UserStoryResponse;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
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
                              "embeddingIndexed": false,
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

    @Operation(
            summary = "Get a user story by id",
            description = "Returns a single user story belonging to the given project and the authenticated tenant.")
    @ApiResponse(
            responseCode = "200",
            description = "Story found",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = UserStoryResponse.class)))
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(path = "/{storyId}", version = ApiVersioning.V1)
    ResponseEntity<UserStoryResponse> getById(
            @Parameter(description = "Project the story belongs to", required = true)
            @PathVariable UUID projectId,
            @Parameter(description = "Story identifier", required = true)
            @PathVariable UUID storyId);

    @Operation(
            summary = "List user stories for a project (backlog)",
            description = """
                    Returns a paginated list of user stories for the given project, scoped to the \
                    authenticated tenant. Supports optional server-side text search and filtering by \
                    status, priority and creation-date range — all filters are applied in the database \
                    and combined with AND. Omitting a filter leaves that dimension unrestricted.""")
    @ApiResponse(
            responseCode = "200",
            description = "Paginated project backlog",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(version = ApiVersioning.V1)
    ResponseEntity<PageResponse<UserStoryResponse>> list(
            @Parameter(description = "Project whose backlog to list", required = true)
            @PathVariable UUID projectId,
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size (max 100)", example = "20")
            @RequestParam(required = false) Integer size,
            @Parameter(description = "Sort field: createdAt | title | priority | status", example = "createdAt")
            @RequestParam(required = false) String sortBy,
            @Parameter(description = "Sort direction: ASC | DESC", example = "DESC")
            @RequestParam(required = false) String sortDirection,
            @Parameter(description = "Case-insensitive substring matched across title, role and action", example = "upload")
            @RequestParam(required = false) String search,
            @Parameter(description = "Filter by review status", example = "DRAFT",
                    schema = @Schema(allowableValues = {"DRAFT", "APPROVED", "REJECTED", "MERGED", "EXPORTED"}))
            @RequestParam(required = false) String status,
            @Parameter(description = "Filter by backlog priority", example = "HIGH",
                    schema = @Schema(allowableValues = {"LOW", "MEDIUM", "HIGH", "CRITICAL"}))
            @RequestParam(required = false) String priority,
            @Parameter(description = "Lower bound on createdAt, inclusive (ISO-8601 instant)", example = "2026-06-01T00:00:00Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdAfter,
            @Parameter(description = "Upper bound on createdAt, exclusive (ISO-8601 instant)", example = "2026-07-01T00:00:00Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdBefore);

    @Operation(
            summary = "Update a user story (manual edit)",
            description = """
                    Edits the core fields of an existing story (title, role, action, benefit, priority, \
                    story points), scoped to the authenticated tenant. This is a straight field update: \
                    it does NOT re-run duplicate detection or re-compute the similarity embedding, so a \
                    manual edit never changes the story's indexed/deduplicated state.""")
    @ApiResponse(
            responseCode = "200",
            description = "Story updated",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = UserStoryResponse.class)))
    @ApiResponseBadRequest
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PutMapping(path = "/{storyId}", version = ApiVersioning.V1)
    ResponseEntity<UserStoryResponse> update(
            @Parameter(description = "Project the story belongs to", required = true)
            @PathVariable UUID projectId,
            @Parameter(description = "Story to update", required = true)
            @PathVariable UUID storyId,
            @Valid @RequestBody UpdateUserStoryRequest request);
}
