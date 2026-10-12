package com.kntro.reqsai.workspace.interfaces.rest.swagger;

import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseNotFound;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.ProjectSetupResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

/**
 * API contract for the setup of a new project: the external sources (Jira, GitHub) it was created with and how far
 * each step has got. The route carries no {@code orgId}; the tenant is taken from the JWT. Implementation lives in
 * {@code controllers.ProjectSetupControllerImpl}.
 */
@RequestMapping(path = ApiVersioning.BASE + "/projects/{projectId}/setup", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Project Setup", description = "The Jira and GitHub sources a project was created with, set up in the background")
public interface ProjectSetupController {

    @Operation(summary = "Get the setup of a project",
            description = """
                    The steps the project asked for when it was created (link Jira, import its stories, connect \
                    repositories), each with its state and counts, and the technology found once repositories are \
                    indexed. Empty steps when it asked for nothing. The same object is pushed on \
                    /topic/projects/{projectId}/setup. Requires INTEGRATION_READ.""")
    @ApiResponse(responseCode = "200", description = "The setup")
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(version = ApiVersioning.V1)
    ResponseEntity<ProjectSetupResponse> get(
            @Parameter(description = "Project UUID") @PathVariable UUID projectId);

    @Operation(summary = "Skip a setup step",
            description = "Stops following the step. Work already started keeps running. Requires INTEGRATION_WRITE.")
    @ApiResponse(responseCode = "200", description = "The setup after skipping")
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(value = "/steps/{stepKey}/skip", version = ApiVersioning.V1)
    ResponseEntity<ProjectSetupResponse> skip(
            @Parameter(description = "Project UUID") @PathVariable UUID projectId,
            @Parameter(description = "JIRA_LINK, JIRA_IMPORT or GITHUB_REPOS") @PathVariable String stepKey);

    @Operation(summary = "Retry a failed setup step",
            description = "Runs a failed step again, on behalf of the caller. 409 when the step did not fail. "
                    + "Requires INTEGRATION_WRITE.")
    @ApiResponse(responseCode = "200", description = "The setup after queuing the retry")
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(value = "/steps/{stepKey}/retry", version = ApiVersioning.V1)
    ResponseEntity<ProjectSetupResponse> retry(
            @Parameter(description = "Project UUID") @PathVariable UUID projectId,
            @Parameter(description = "JIRA_LINK, JIRA_IMPORT or GITHUB_REPOS") @PathVariable String stepKey,
            Authentication authentication);
}
