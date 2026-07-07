package com.kntro.reqsai.integrations.interfaces.rest.controllers;

import com.kntro.reqsai.integrations.application.command.DeleteProjectTargetCommand;
import com.kntro.reqsai.integrations.application.command.PushAllStoriesCommand;
import com.kntro.reqsai.integrations.application.command.PushStoryCommand;
import com.kntro.reqsai.integrations.application.handler.DeleteProjectTargetCommandHandler;
import com.kntro.reqsai.integrations.application.handler.GetProjectTargetQueryHandler;
import com.kntro.reqsai.integrations.application.handler.PushAllStoriesCommandHandler;
import com.kntro.reqsai.integrations.application.handler.PushStoryCommandHandler;
import com.kntro.reqsai.integrations.application.handler.SaveProjectTargetCommandHandler;
import com.kntro.reqsai.integrations.application.query.GetProjectTargetQuery;
import com.kntro.reqsai.integrations.interfaces.rest.dto.request.SaveProjectTargetRequest;
import com.kntro.reqsai.integrations.interfaces.rest.dto.response.BatchPushResponse;
import com.kntro.reqsai.integrations.interfaces.rest.dto.response.JiraPushResultResponse;
import com.kntro.reqsai.integrations.interfaces.rest.dto.response.ProjectJiraTargetResponse;
import com.kntro.reqsai.integrations.interfaces.rest.mappers.request.IntegrationRequestMapper;
import com.kntro.reqsai.integrations.interfaces.rest.mappers.response.IntegrationResponseMapper;
import com.kntro.reqsai.integrations.interfaces.rest.swagger.ProjectIntegrationController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Project-level integration endpoints. Target read/write/delete are gated by project
 * {@code INTEGRATION_WRITE}; story pushes by {@code INTEGRATION_SYNC}, via the tenant-bound
 * {@code @authz.projectPermission} variant (these routes carry no {@code orgId}).
 */
@RestController
@RequiredArgsConstructor
public class ProjectIntegrationControllerImpl implements ProjectIntegrationController {

    private final GetProjectTargetQueryHandler getTarget;
    private final SaveProjectTargetCommandHandler saveTarget;
    private final DeleteProjectTargetCommandHandler deleteTarget;
    private final PushStoryCommandHandler pushStory;
    private final PushAllStoriesCommandHandler pushAllStories;

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
    public ResponseEntity<BatchPushResponse> pushAllStories(UUID projectId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(IntegrationResponseMapper.toResponse(
                pushAllStories.handle(new PushAllStoriesCommand(projectId, requestedBy))));
    }
}
