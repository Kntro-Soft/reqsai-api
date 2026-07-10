package com.kntro.reqsai.gateway.interfaces.rest.controllers;

import com.kntro.reqsai.gateway.application.command.DeleteProjectTargetCommand;
import com.kntro.reqsai.gateway.application.command.ImportJiraStoriesCommand;
import com.kntro.reqsai.gateway.application.command.PushAllStoriesCommand;
import com.kntro.reqsai.gateway.application.command.PushStoryCommand;
import com.kntro.reqsai.gateway.application.handler.DeleteProjectTargetCommandHandler;
import com.kntro.reqsai.gateway.application.handler.GetIntegrationJobQueryHandler;
import com.kntro.reqsai.gateway.application.handler.GetProjectTargetQueryHandler;
import com.kntro.reqsai.gateway.application.handler.ImportJiraStoriesCommandHandler;
import com.kntro.reqsai.gateway.application.handler.ListIntegrationJobsQueryHandler;
import com.kntro.reqsai.gateway.application.handler.PreviewJiraImportQueryHandler;
import com.kntro.reqsai.gateway.application.handler.PushAllStoriesCommandHandler;
import com.kntro.reqsai.gateway.application.handler.PushStoryCommandHandler;
import com.kntro.reqsai.gateway.application.handler.SaveProjectTargetCommandHandler;
import com.kntro.reqsai.gateway.application.query.GetIntegrationJobQuery;
import com.kntro.reqsai.gateway.application.query.GetProjectTargetQuery;
import com.kntro.reqsai.gateway.application.query.ListIntegrationJobsQuery;
import com.kntro.reqsai.gateway.application.query.PreviewJiraImportQuery;
import com.kntro.reqsai.gateway.interfaces.rest.dto.request.ImportJiraStoriesRequest;
import com.kntro.reqsai.gateway.interfaces.rest.dto.request.PushAllStoriesRequest;
import com.kntro.reqsai.gateway.interfaces.rest.dto.request.SaveProjectTargetRequest;
import com.kntro.reqsai.gateway.interfaces.rest.dto.response.IntegrationJobResponse;
import com.kntro.reqsai.gateway.interfaces.rest.dto.response.JiraImportPreviewResponse;
import com.kntro.reqsai.gateway.interfaces.rest.dto.response.JiraPushResultResponse;
import com.kntro.reqsai.gateway.interfaces.rest.dto.response.ProjectJiraTargetResponse;
import com.kntro.reqsai.gateway.interfaces.rest.mappers.request.IntegrationRequestMapper;
import com.kntro.reqsai.gateway.interfaces.rest.mappers.response.IntegrationResponseMapper;
import com.kntro.reqsai.gateway.interfaces.rest.swagger.ProjectIntegrationController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Project-level integration endpoints. Target read/write/delete are gated by project
 * {@code INTEGRATION_WRITE}; story pushes/imports by {@code INTEGRATION_SYNC}, via the tenant-bound
 * {@code @authz.projectPermission} variant (these routes carry no {@code orgId}).
 *
 * <p>Import and push-all are <strong>asynchronous</strong>: they answer {@code 202 Accepted} with an
 * {@link IntegrationJobResponse} snapshot; live progress streams on
 * {@code /topic/projects/{projectId}/integration-jobs} and the job endpoints serve reload recovery.
 * The single-story push and the import preview stay synchronous. Job reads are gated by
 * {@code INTEGRATION_READ} (they expose progress state, not sync capability — consistent with the
 * target GET).
 */
@RestController
@RequiredArgsConstructor
public class ProjectIntegrationControllerImpl implements ProjectIntegrationController {

    private final GetProjectTargetQueryHandler getTarget;
    private final SaveProjectTargetCommandHandler saveTarget;
    private final DeleteProjectTargetCommandHandler deleteTarget;
    private final PushStoryCommandHandler pushStory;
    private final PushAllStoriesCommandHandler pushAllStories;
    private final PreviewJiraImportQueryHandler previewImport;
    private final ImportJiraStoriesCommandHandler importStories;
    private final ListIntegrationJobsQueryHandler listJobs;
    private final GetIntegrationJobQueryHandler getJob;

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'INTEGRATION_READ', authentication)")
    public ResponseEntity<ProjectJiraTargetResponse> getTarget(UUID projectId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(IntegrationResponseMapper.toResponse(
                getTarget.handle(new GetProjectTargetQuery(projectId, requestedBy))));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'INTEGRATION_WRITE', authentication)")
    public ResponseEntity<ProjectJiraTargetResponse> saveTarget(
            UUID projectId, SaveProjectTargetRequest request, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(IntegrationResponseMapper.toResponse(
                saveTarget.handle(IntegrationRequestMapper.toCommand(projectId, request, requestedBy))));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'INTEGRATION_DELETE', authentication)")
    public ResponseEntity<Void> deleteTarget(UUID projectId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        deleteTarget.handle(new DeleteProjectTargetCommand(projectId, requestedBy));
        return ResponseEntity.noContent().build();
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'INTEGRATION_SYNC', authentication)")
    public ResponseEntity<JiraPushResultResponse> pushStory(UUID projectId, UUID storyId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(IntegrationResponseMapper.toResponse(
                pushStory.handle(new PushStoryCommand(projectId, storyId, requestedBy))));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'INTEGRATION_SYNC', authentication)")
    public ResponseEntity<IntegrationJobResponse> pushAllStories(
            UUID projectId, PushAllStoriesRequest request, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        List<UUID> storyIds = request == null ? null : request.storyIds();
        return ResponseEntity.accepted().body(IntegrationResponseMapper.toResponse(
                pushAllStories.handle(new PushAllStoriesCommand(projectId, storyIds, requestedBy))));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'INTEGRATION_SYNC', authentication)")
    public ResponseEntity<JiraImportPreviewResponse> previewImport(UUID projectId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(IntegrationResponseMapper.toResponse(
                previewImport.handle(new PreviewJiraImportQuery(projectId, requestedBy))));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'INTEGRATION_SYNC', authentication)")
    public ResponseEntity<IntegrationJobResponse> importStories(
            UUID projectId, ImportJiraStoriesRequest request, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        List<String> issueKeys = request == null ? null : request.issueKeys();
        return ResponseEntity.accepted().body(IntegrationResponseMapper.toResponse(
                importStories.handle(new ImportJiraStoriesCommand(projectId, issueKeys, requestedBy))));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'INTEGRATION_READ', authentication)")
    public ResponseEntity<List<IntegrationJobResponse>> listJobs(
            UUID projectId, boolean active, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(listJobs.handle(new ListIntegrationJobsQuery(projectId, active, requestedBy))
                .stream().map(IntegrationResponseMapper::toResponse).toList());
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'INTEGRATION_READ', authentication)")
    public ResponseEntity<IntegrationJobResponse> getJob(UUID projectId, UUID jobId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(IntegrationResponseMapper.toResponse(
                getJob.handle(new GetIntegrationJobQuery(projectId, jobId, requestedBy))));
    }
}
