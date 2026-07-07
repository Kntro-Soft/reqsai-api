package com.kntro.reqsai.discovery.interfaces.rest.swagger;

import com.kntro.reqsai.discovery.domain.model.SuggestionStatus;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.SuggestionResponse;
import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
import com.kntro.reqsai.shared.interfaces.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * API contract for project-scoped suggestion listing; implemented by
 * {@code controllers.ProjectSuggestionControllerImpl}. Lets the frontend surface "N pending from
 * previous sessions" across every session of the project. All routes are tenant-scoped — the active
 * schema is resolved from the JWT {@code orgId}.
 */
@RequestMapping(path = ApiVersioning.BASE + "/projects/{projectId}/suggestions", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Suggestions", description = "AI-generated proposals pending analyst review before backlog commit")
public interface ProjectSuggestionController {

    @Operation(
            summary = "List a project's suggestions by status",
            description = """
                    Returns a paginated list of the project's suggestions filtered by review status \
                    (defaults to **PENDING**), across all of its discovery sessions. Powers the \
                    "N pending from previous sessions" triage view.""")
    @ApiResponse(
            responseCode = "200",
            description = "Paginated list of suggestions",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(version = ApiVersioning.V1)
    ResponseEntity<PageResponse<SuggestionResponse>> list(
            @Parameter(description = "Project whose suggestions to list", required = true)
            @PathVariable UUID projectId,
            @Parameter(description = "Review status filter (default PENDING)", example = "PENDING")
            @RequestParam(required = false) SuggestionStatus status,
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size (max 100)", example = "20")
            @RequestParam(required = false) Integer size,
            @Parameter(description = "Sort field: createdAt", example = "createdAt")
            @RequestParam(required = false) String sortBy,
            @Parameter(description = "Sort direction: ASC | DESC", example = "DESC")
            @RequestParam(required = false) String sortDirection);
}
