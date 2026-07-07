package com.kntro.reqsai.gateway.interfaces.rest.swagger;

import com.kntro.reqsai.gateway.interfaces.rest.dto.request.ImportJiraStoriesRequest;
import com.kntro.reqsai.gateway.interfaces.rest.dto.request.SaveProjectTargetRequest;
import com.kntro.reqsai.gateway.interfaces.rest.dto.response.BatchPushResponse;
import com.kntro.reqsai.gateway.interfaces.rest.dto.response.JiraImportPreviewResponse;
import com.kntro.reqsai.gateway.interfaces.rest.dto.response.JiraImportResponse;
import com.kntro.reqsai.gateway.interfaces.rest.dto.response.JiraPushResultResponse;
import com.kntro.reqsai.gateway.interfaces.rest.dto.response.ProjectJiraTargetResponse;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@RequestMapping(
        path = ApiVersioning.BASE + "/projects/{projectId}/integration/jira",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Project Integration", description = "Project-level Jira push target and story export")
public interface ProjectIntegrationController {

    @Operation(summary = "Get the project's Jira target",
            description = "Returns the project's configured Jira push target, 404 when none is set.")
    @ApiResponse(responseCode = "200", description = "Jira target",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ProjectJiraTargetResponse.class)))
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(value = "/target", version = ApiVersioning.V1)
    ResponseEntity<ProjectJiraTargetResponse> getTarget(
            @Parameter(description = "Project UUID") @PathVariable UUID projectId,
            Authentication authentication);

    @Operation(summary = "Set the project's Jira target",
            description = "Creates or replaces the single Jira push target for the project (upsert).")
    @ApiResponse(responseCode = "200", description = "Jira target saved",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ProjectJiraTargetResponse.class)))
    @ApiResponseBadRequest
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PutMapping(value = "/target", version = ApiVersioning.V1)
    ResponseEntity<ProjectJiraTargetResponse> saveTarget(
            @Parameter(description = "Project UUID") @PathVariable UUID projectId,
            @Valid @RequestBody SaveProjectTargetRequest request,
            Authentication authentication);

    @Operation(summary = "Delete the project's Jira target",
            description = "Removes the project's Jira push target.")
    @ApiResponse(responseCode = "204", description = "Jira target deleted")
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @DeleteMapping(value = "/target", version = ApiVersioning.V1)
    ResponseEntity<Void> deleteTarget(
            @Parameter(description = "Project UUID") @PathVariable UUID projectId,
            Authentication authentication);

    @Operation(summary = "Push one story to Jira",
            description = "Pushes a single story to the project's Jira target. 409 when no target is configured.")
    @ApiResponse(responseCode = "200", description = "Story pushed",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = JiraPushResultResponse.class)))
    @ApiResponseConflict
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(value = "/stories/{storyId}/push", version = ApiVersioning.V1)
    ResponseEntity<JiraPushResultResponse> pushStory(
            @Parameter(description = "Project UUID") @PathVariable UUID projectId,
            @Parameter(description = "Story UUID") @PathVariable UUID storyId,
            Authentication authentication);

    @Operation(summary = "Push all stories to Jira",
            description = """
                    Pushes every project story to the Jira target. Per-story failures are captured in the
                    results and do not abort the batch. 409 when no target is configured.""")
    @ApiResponse(responseCode = "200", description = "Batch push result",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = BatchPushResponse.class)))
    @ApiResponseConflict
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(value = "/stories/push-all", version = ApiVersioning.V1)
    ResponseEntity<BatchPushResponse> pushAllStories(
            @Parameter(description = "Project UUID") @PathVariable UUID projectId,
            Authentication authentication);

    @Operation(summary = "Preview a Jira import",
            description = """
                    Lists the Jira issues eligible for import from the project's target and flags likely
                    duplicates (detected via the discovery similarity path, without creating anything).
                    409 when no target is configured.""")
    @ApiResponse(responseCode = "200", description = "Import preview",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = JiraImportPreviewResponse.class)))
    @ApiResponseConflict
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(value = "/import/preview", version = ApiVersioning.V1)
    ResponseEntity<JiraImportPreviewResponse> previewImport(
            @Parameter(description = "Project UUID") @PathVariable UUID projectId,
            Authentication authentication);

    @Operation(summary = "Import Jira issues as stories",
            description = """
                    Pulls Jira issues from the project's target and creates them as user stories (LLM
                    mapping + duplicate detection reused from discovery). Body {issueKeys?} restricts the
                    import; omit/empty imports all eligible issues. Per-issue failures are captured without
                    aborting the batch; duplicates are counted as skipped. 409 when no target is configured.""")
    @ApiResponse(responseCode = "200", description = "Import result",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = JiraImportResponse.class)))
    @ApiResponseConflict
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(value = "/import", version = ApiVersioning.V1)
    ResponseEntity<JiraImportResponse> importStories(
            @Parameter(description = "Project UUID") @PathVariable UUID projectId,
            @RequestBody(required = false) ImportJiraStoriesRequest request,
            Authentication authentication);
}
