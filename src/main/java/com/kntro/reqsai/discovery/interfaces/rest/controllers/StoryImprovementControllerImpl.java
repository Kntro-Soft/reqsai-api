package com.kntro.reqsai.discovery.interfaces.rest.controllers;

import com.kntro.reqsai.discovery.application.handler.ApplyStoryImprovementCommandHandler;
import com.kntro.reqsai.discovery.application.handler.ProposeStoryImprovementQueryHandler;
import com.kntro.reqsai.discovery.application.query.ProposeStoryImprovementQuery;
import com.kntro.reqsai.discovery.interfaces.rest.dto.request.ApplyStoryImprovementRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.ApplyStoryImprovementResponse;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.StoryImprovementResponse;
import com.kntro.reqsai.discovery.interfaces.rest.mappers.request.StoryImprovementRequestMapper;
import com.kntro.reqsai.discovery.interfaces.rest.mappers.response.StoryImprovementResponseMapper;
import com.kntro.reqsai.discovery.interfaces.rest.swagger.StoryImprovementController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Implementation of the {@link StoryImprovementController} API contract. */
@RestController
@RequiredArgsConstructor
public class StoryImprovementControllerImpl implements StoryImprovementController {

    private final ProposeStoryImprovementQueryHandler proposeImprovement;
    private final ApplyStoryImprovementCommandHandler applyImprovement;

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'STORY_WRITE', authentication)")
    public ResponseEntity<StoryImprovementResponse> propose(UUID projectId, UUID storyId) {
        return ResponseEntity.ok(StoryImprovementResponseMapper.toResponse(
                proposeImprovement.handle(new ProposeStoryImprovementQuery(projectId, storyId))));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'STORY_WRITE', authentication)")
    public ResponseEntity<ApplyStoryImprovementResponse> apply(UUID projectId, UUID storyId,
                                                               ApplyStoryImprovementRequest request) {
        return ResponseEntity.ok(StoryImprovementResponseMapper.toResponse(
                applyImprovement.handle(StoryImprovementRequestMapper.toCommand(projectId, storyId, request))));
    }
}
