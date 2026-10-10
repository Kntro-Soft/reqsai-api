package com.kntro.reqsai.codebase.interfaces.rest.swagger;

import com.kntro.reqsai.codebase.interfaces.rest.dto.request.CompleteGitHubInstallRequest;
import com.kntro.reqsai.codebase.interfaces.rest.dto.response.GitHubConnectionResponse;
import com.kntro.reqsai.codebase.interfaces.rest.dto.response.GitHubInstallResultResponse;
import com.kntro.reqsai.codebase.interfaces.rest.dto.response.GitHubInstallUrlResponse;
import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseBadRequest;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseNotFound;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

/**
 * API contract for connecting an organization's GitHub through the ReqsAI GitHub App, implemented by
 * {@code controllers.OrganizationGitHubControllerImpl}. Organization owners and admins only.
 */
@RequestMapping(path = ApiVersioning.BASE + "/organizations/{orgId}/code/github",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Code", description = "Connected repositories and the module map the copilot reads")
public interface OrganizationGitHubController {

    @Operation(summary = "Get the organization's GitHub connection",
            description = "Whether the server has the GitHub App, and the GitHub accounts the organization installed it on.")
    @ApiResponse(responseCode = "200", description = "The connection")
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(version = ApiVersioning.V1)
    ResponseEntity<GitHubConnectionResponse> get(
            @Parameter(description = "Organization", required = true) @PathVariable UUID orgId);

    @Operation(summary = "Start installing the GitHub App",
            description = """
                    The GitHub page where the user installs the ReqsAI GitHub App on a GitHub account and picks \
                    the repositories ReqsAI may read (read-only). It carries a signed state, valid 30 minutes, \
                    that GitHub sends back to the setup URL. 409 CODE_HOST_APP_NOT_CONFIGURED without the App.""")
    @ApiResponse(responseCode = "200", description = "The install URL")
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(value = "/install", version = ApiVersioning.V1)
    ResponseEntity<GitHubInstallUrlResponse> startInstall(
            @Parameter(description = "Organization", required = true) @PathVariable UUID orgId,
            @Parameter(hidden = true) Authentication authentication);

    @Operation(summary = "Complete a GitHub App install",
            description = """
                    Links the installation GitHub redirected back with. A new link needs the signed state of \
                    this organization and user, and the OAuth code of the redirect must show that the GitHub \
                    user can access the installation (403 CODE_HOST_INSTALLATION_FORBIDDEN otherwise). An \
                    installation already linked is refreshed. REQUESTED when a GitHub organization member only \
                    asked an owner to approve the install.""")
    @ApiResponse(responseCode = "200", description = "LINKED with the installation, or REQUESTED")
    @ApiResponseBadRequest
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(value = "/installations", consumes = MediaType.APPLICATION_JSON_VALUE, version = ApiVersioning.V1)
    ResponseEntity<GitHubInstallResultResponse> completeInstall(
            @Parameter(description = "Organization", required = true) @PathVariable UUID orgId,
            @Valid @RequestBody CompleteGitHubInstallRequest request,
            @Parameter(hidden = true) Authentication authentication);

    @Operation(summary = "Disconnect a GitHub account",
            description = """
                    Unlinks the installation from the organization. Its repositories keep their module map but \
                    stop updating. The App itself is uninstalled on GitHub.""")
    @ApiResponse(responseCode = "204", description = "Unlinked")
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @DeleteMapping(value = "/installations/{installationId}", version = ApiVersioning.V1)
    ResponseEntity<Void> disconnect(
            @Parameter(description = "Organization", required = true) @PathVariable UUID orgId,
            @Parameter(description = "GitHub installation id", required = true) @PathVariable long installationId);
}
