package com.kntro.reqsai.codebase.interfaces.rest.swagger;

import com.kntro.reqsai.codebase.interfaces.rest.dto.request.ConnectRepositoryRequest;
import com.kntro.reqsai.codebase.interfaces.rest.dto.response.CodeModuleResponse;
import com.kntro.reqsai.codebase.interfaces.rest.dto.response.CodeRepositoryResponse;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.UUID;

/**
 * API contract for the client's code connected to a project, implemented by
 * {@code controllers.CodeRepositoryControllerImpl}. Tenant-scoped through the JWT {@code orgId}.
 */
@RequestMapping(path = ApiVersioning.BASE + "/projects/{projectId}/code/repositories",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Code", description = "Connected repositories and the module map the copilot reads")
public interface CodeRepositoryController {

    @Operation(summary = "Connect a GitHub repository",
            description = """
                    Checks with GitHub that the repository and branch exist (a private one needs a read-only \
                    token, stored encrypted) and starts indexing it in the background: the code is read once, \
                    secrets are removed, and every module gets a summary, its capabilities and the business \
                    rules it implements. Raw code is never stored. Requires INTEGRATION_WRITE.""")
    @ApiResponse(responseCode = "201", description = "The repository, indexing")
    @ApiResponseBadRequest
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, version = ApiVersioning.V1)
    ResponseEntity<CodeRepositoryResponse> connect(
            @Parameter(description = "Project", required = true) @PathVariable UUID projectId,
            @Valid @RequestBody ConnectRepositoryRequest request);

    @Operation(summary = "List connected repositories",
            description = "The project's repositories with their index status and detected profile. Requires INTEGRATION_READ.")
    @ApiResponse(responseCode = "200", description = "Repositories, oldest first")
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(version = ApiVersioning.V1)
    ResponseEntity<List<CodeRepositoryResponse>> list(
            @Parameter(description = "Project", required = true) @PathVariable UUID projectId);

    @Operation(summary = "Reindex a repository",
            description = "Reads the branch's head again; only modules whose files changed are summarized again. "
                    + "409 CODE_REPOSITORY_INDEXING while a run is in progress. Requires INTEGRATION_WRITE.")
    @ApiResponse(responseCode = "200", description = "The repository, indexing")
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(path = "/{repositoryId}/reindex", version = ApiVersioning.V1)
    ResponseEntity<CodeRepositoryResponse> reindex(
            @Parameter(description = "Project", required = true) @PathVariable UUID projectId,
            @Parameter(description = "Repository", required = true) @PathVariable UUID repositoryId);

    @Operation(summary = "Disconnect a repository",
            description = "Forgets the repository, its token and every module indexed from it. Requires INTEGRATION_DELETE.")
    @ApiResponse(responseCode = "204", description = "Disconnected")
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @DeleteMapping(path = "/{repositoryId}", version = ApiVersioning.V1)
    ResponseEntity<Void> disconnect(
            @Parameter(description = "Project", required = true) @PathVariable UUID projectId,
            @Parameter(description = "Repository", required = true) @PathVariable UUID repositoryId);

    @Operation(summary = "List a repository's modules",
            description = "The module map of the repository, ordered by path. Requires INTEGRATION_READ.")
    @ApiResponse(responseCode = "200", description = "Modules")
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(path = "/{repositoryId}/modules", version = ApiVersioning.V1)
    ResponseEntity<List<CodeModuleResponse>> modules(
            @Parameter(description = "Project", required = true) @PathVariable UUID projectId,
            @Parameter(description = "Repository", required = true) @PathVariable UUID repositoryId);
}
