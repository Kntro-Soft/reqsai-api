package com.kntro.reqsai.discovery.interfaces.rest.controllers;

import com.kntro.reqsai.discovery.application.handler.GetStorySourcesQueryHandler;
import com.kntro.reqsai.discovery.application.query.GetStorySourcesQuery;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.StorySourcesResponse;
import com.kntro.reqsai.discovery.interfaces.rest.mappers.response.StorySourcesResponseMapper;
import com.kntro.reqsai.discovery.interfaces.rest.swagger.StorySourceController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Implementation of the {@link StorySourceController} API contract. */
@RestController
@RequiredArgsConstructor
public class StorySourceControllerImpl implements StorySourceController {

    private final GetStorySourcesQueryHandler getStorySources;

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'STORY_READ', authentication)")
    public ResponseEntity<StorySourcesResponse> getSources(UUID projectId, UUID storyId) {
        return ResponseEntity.ok(StorySourcesResponseMapper.toResponse(
                getStorySources.handle(new GetStorySourcesQuery(projectId, storyId))));
    }
}
