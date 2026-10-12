package com.kntro.reqsai.overview.interfaces.rest.swagger;

import com.kntro.reqsai.overview.interfaces.rest.dto.response.OrganizationOverviewResponse;
import com.kntro.reqsai.overview.interfaces.rest.dto.response.ProjectOverviewResponse;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

/**
 * API contract for the project and organization dashboards; implemented by
 * {@code controllers.OverviewControllerImpl}. Read-only, tenant-scoped (the
 * schema comes from the JWT {@code orgId}).
 */
@RequestMapping(path = ApiVersioning.BASE, produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Overview", description = "Read-only dashboards: aggregate figures of a project and of an organization")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
public interface OverviewController {

    @Operation(summary = "Project overview", description = """
            Aggregate figures of one project, all computed in SQL: stories by status and priority, \
            story points, coverage (acceptance criteria, origin quote, linked code), sessions and stories \
            per week over the last 12 weeks, how often the copilot's proposals were accepted, the \
            glossary, constraints and documents, and the connected code. Each section follows the \
            project permission guarding the same data (STORY_READ, SESSION_READ, GLOSSARY_READ, \
            CONSTRAINT_READ, DOCUMENT_READ, MEMBER_READ, INTEGRATION_READ) and is null when the caller \
            lacks it; 403 when they hold none of them.""")
    @ApiResponse(responseCode = "200", description = "The project's overview", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProjectOverviewResponse.class)))
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @GetMapping(path = "/projects/{projectId}/overview", version = ApiVersioning.V1)
    ResponseEntity<ProjectOverviewResponse> project(
            @Parameter(description = "Project to summarize", required = true) @PathVariable UUID projectId,
            Authentication authentication);

    @Operation(summary = "Organization overview", description = """
            Aggregate figures across the projects the caller may see (every project for an owner or \
            admin, the assigned ones for a regular member): projects, stories by status, sessions and \
            stories per week, copilot acceptance, connected code, the busiest projects and, for owners \
            and admins only, the headcount. Requires membership of the organization, which must be the \
            one the token is bound to.""")
    @ApiResponse(responseCode = "200", description = "The organization's overview", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = OrganizationOverviewResponse.class)))
    @ApiStandardErrorResponses
    @GetMapping(path = "/organizations/{organizationId}/overview", version = ApiVersioning.V1)
    ResponseEntity<OrganizationOverviewResponse> organization(
            @Parameter(description = "Organization to summarize", required = true) @PathVariable UUID organizationId,
            Authentication authentication);
}
