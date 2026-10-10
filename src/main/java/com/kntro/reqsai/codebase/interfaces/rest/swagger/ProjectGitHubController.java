package com.kntro.reqsai.codebase.interfaces.rest.swagger;

import com.kntro.reqsai.codebase.interfaces.rest.dto.response.GitHubConnectionResponse;
import com.kntro.reqsai.codebase.interfaces.rest.dto.response.GitHubRepositoryResponse;
import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.UUID;

/**
 * API contract for a project's view of the organization's GitHub connection, implemented by
 * {@code controllers.ProjectGitHubControllerImpl}: what the Code page needs to offer the repository picker.
 */
@RequestMapping(path = ApiVersioning.BASE + "/projects/{projectId}/code/github",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Code", description = "Connected repositories and the module map the copilot reads")
public interface ProjectGitHubController {

    @Operation(summary = "Get the organization's GitHub connection from a project",
            description = "Whether the GitHub App is available and on which accounts it is installed. Requires INTEGRATION_READ.")
    @ApiResponse(responseCode = "200", description = "The connection")
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(version = ApiVersioning.V1)
    ResponseEntity<GitHubConnectionResponse> get(
            @Parameter(description = "Project", required = true) @PathVariable UUID projectId);

    @Operation(summary = "List the repositories the GitHub App shares",
            description = """
                    Every repository the organization's installations share with ReqsAI, most recently pushed \
                    first, marked when the project already reads it. Requires INTEGRATION_WRITE.""")
    @ApiResponse(responseCode = "200", description = "Repositories")
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(value = "/repositories", version = ApiVersioning.V1)
    ResponseEntity<List<GitHubRepositoryResponse>> repositories(
            @Parameter(description = "Project", required = true) @PathVariable UUID projectId);
}
