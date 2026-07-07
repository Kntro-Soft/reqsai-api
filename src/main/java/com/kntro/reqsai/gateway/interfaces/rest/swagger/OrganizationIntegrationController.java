package com.kntro.reqsai.gateway.interfaces.rest.swagger;

import com.kntro.reqsai.gateway.interfaces.rest.dto.request.ConnectJiraRequest;
import com.kntro.reqsai.gateway.interfaces.rest.dto.request.JiraOAuthCallbackRequest;
import com.kntro.reqsai.gateway.interfaces.rest.dto.response.ConnectionTestResponse;
import com.kntro.reqsai.gateway.interfaces.rest.dto.response.IntegrationConnectionResponse;
import com.kntro.reqsai.gateway.interfaces.rest.dto.response.JiraIssueTypeResponse;
import com.kntro.reqsai.gateway.interfaces.rest.dto.response.JiraOAuthAuthorizeUrlResponse;
import com.kntro.reqsai.gateway.interfaces.rest.dto.response.JiraProjectResponse;
import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseBadRequest;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseConflict;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseNotFound;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@RequestMapping(
        path = ApiVersioning.BASE + "/organizations/{orgId}/integrations",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Organization Integrations", description = "Org-level third-party integration connections (Jira)")
public interface OrganizationIntegrationController {

    @Operation(summary = "List organization integration connections",
            description = "Returns the organization's integration connections. The API token is never returned.")
    @ApiResponse(responseCode = "200", description = "Connections listed",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(version = ApiVersioning.V1)
    ResponseEntity<List<IntegrationConnectionResponse>> listConnections(
            @Parameter(description = "Organization UUID") @PathVariable UUID orgId,
            Authentication authentication);

    @Operation(summary = "Connect a Jira integration",
            description = """
                    Verifies the supplied Jira credentials against Jira Cloud and, on success, stores an
                    encrypted connection. Returns 409 when an active connection already exists, and
                    401/502 when Jira rejects or is unreachable.""")
    @ApiResponse(responseCode = "201", description = "Jira connection created",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = IntegrationConnectionResponse.class)))
    @ApiResponseBadRequest
    @ApiResponseConflict
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(value = "/jira", version = ApiVersioning.V1)
    ResponseEntity<IntegrationConnectionResponse> connectJira(
            @Parameter(description = "Organization UUID") @PathVariable UUID orgId,
            @Valid @RequestBody ConnectJiraRequest request,
            Authentication authentication);

    @Operation(summary = "Get the Jira OAuth authorize URL",
            description = """
                    Returns the Atlassian authorize URL (with a stateless signed `state`) to redirect the
                    user into the OAuth 2.0 (3LO) consent flow. Responds `501 JIRA_OAUTH_NOT_CONFIGURED`
                    when the deployment has no Jira OAuth app configured (the UI disables the button).""")
    @ApiResponse(responseCode = "200", description = "Authorize URL + state",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = JiraOAuthAuthorizeUrlResponse.class)))
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(value = "/jira/oauth/authorize-url", version = ApiVersioning.V1)
    ResponseEntity<JiraOAuthAuthorizeUrlResponse> jiraOAuthAuthorizeUrl(
            @Parameter(description = "Organization UUID") @PathVariable UUID orgId,
            Authentication authentication);

    @Operation(summary = "Complete the Jira OAuth callback",
            description = """
                    Validates the signed `state`, exchanges the authorization `code`, and discovers the
                    accessible Atlassian sites. If a `cloudId` is supplied (or exactly one site exists) an
                    encrypted OAUTH2 connection is saved and returned (`IntegrationConnectionResponse`).
                    If multiple sites exist and no `cloudId` is given, returns `200 {sites:[...]}` WITHOUT
                    saving, for the frontend to re-POST with a chosen `cloudId`. `409` when a connection
                    already exists; `400 JIRA_OAUTH_STATE_INVALID` on a bad state;
                    `501 JIRA_OAUTH_NOT_CONFIGURED` when OAuth is unconfigured.""")
    @ApiResponse(responseCode = "200",
            description = "OAUTH2 connection saved, or the list of sites to choose from",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
    @ApiResponseBadRequest
    @ApiResponseConflict
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(value = "/jira/oauth/callback", version = ApiVersioning.V1)
    ResponseEntity<Object> jiraOAuthCallback(
            @Parameter(description = "Organization UUID") @PathVariable UUID orgId,
            @Valid @RequestBody JiraOAuthCallbackRequest request,
            Authentication authentication);

    @Operation(summary = "Test an integration connection",
            description = "Re-verifies the stored credential against the provider. Never fails the request.")
    @ApiResponse(responseCode = "200", description = "Test result",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ConnectionTestResponse.class)))
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(value = "/{connectionId}/test", version = ApiVersioning.V1)
    ResponseEntity<ConnectionTestResponse> testConnection(
            @Parameter(description = "Organization UUID") @PathVariable UUID orgId,
            @Parameter(description = "Connection UUID") @PathVariable UUID connectionId,
            Authentication authentication);

    @Operation(summary = "Delete an integration connection",
            description = "Removes the connection; project targets referencing it are cascaded away.")
    @ApiResponse(responseCode = "204", description = "Connection deleted")
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @DeleteMapping(value = "/{connectionId}", version = ApiVersioning.V1)
    ResponseEntity<Void> deleteConnection(
            @Parameter(description = "Organization UUID") @PathVariable UUID orgId,
            @Parameter(description = "Connection UUID") @PathVariable UUID connectionId,
            Authentication authentication);

    @Operation(summary = "List Jira projects for a connection",
            description = "Lists the Jira projects visible to the connection's credentials.")
    @ApiResponse(responseCode = "200", description = "Jira projects",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(value = "/{connectionId}/jira/projects", version = ApiVersioning.V1)
    ResponseEntity<List<JiraProjectResponse>> listJiraProjects(
            @Parameter(description = "Organization UUID") @PathVariable UUID orgId,
            @Parameter(description = "Connection UUID") @PathVariable UUID connectionId,
            Authentication authentication);

    @Operation(summary = "List Jira issue types",
            description = "Lists the Jira issue types available to the connection for the given project key.")
    @ApiResponse(responseCode = "200", description = "Jira issue types",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(value = "/{connectionId}/jira/issue-types", version = ApiVersioning.V1)
    ResponseEntity<List<JiraIssueTypeResponse>> listJiraIssueTypes(
            @Parameter(description = "Organization UUID") @PathVariable UUID orgId,
            @Parameter(description = "Connection UUID") @PathVariable UUID connectionId,
            @Parameter(description = "Jira project key", example = "PAY") @RequestParam String projectKey,
            Authentication authentication);
}
