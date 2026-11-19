package com.kntro.reqsai.discovery.interfaces.rest.controllers;

import com.kntro.reqsai.discovery.application.command.EvaluateStoryQualityCommand;
import com.kntro.reqsai.discovery.application.handler.EvaluateStoryQualityCommandHandler;
import com.kntro.reqsai.discovery.application.handler.GetStoryQualityQueryHandler;
import com.kntro.reqsai.discovery.application.query.GetStoryQualityQuery;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.StoryQualityResponse;
import com.kntro.reqsai.discovery.interfaces.rest.mappers.response.StoryQualityResponseMapper;
import com.kntro.reqsai.discovery.interfaces.rest.swagger.StoryQualityController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Implementation of the {@link StoryQualityController} API contract. */
@RestController
@RequiredArgsConstructor
public class StoryQualityControllerImpl implements StoryQualityController {

    private final EvaluateStoryQualityCommandHandler evaluateStoryQuality;
    private final GetStoryQualityQueryHandler getStoryQuality;

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'STORY_WRITE', authentication)")
    public ResponseEntity<StoryQualityResponse> evaluate(UUID projectId, UUID storyId) {
        return ResponseEntity.ok(StoryQualityResponseMapper.toResponse(
                evaluateStoryQuality.handle(new EvaluateStoryQualityCommand(projectId, storyId))));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'STORY_READ', authentication)")
    public ResponseEntity<StoryQualityResponse> get(UUID projectId, UUID storyId) {
        return ResponseEntity.ok(StoryQualityResponseMapper.toResponse(
                getStoryQuality.handle(new GetStoryQualityQuery(projectId, storyId))));
    }
}
