package com.kntro.reqsai.discovery.interfaces.rest.controllers;

import com.kntro.reqsai.discovery.application.handler.GetSessionStoryQueryHandler;
import com.kntro.reqsai.discovery.application.handler.ListSessionStoriesQueryHandler;
import com.kntro.reqsai.discovery.application.query.GetSessionStoryQuery;
import com.kntro.reqsai.discovery.application.query.ListSessionStoriesQuery;
import com.kntro.reqsai.discovery.application.service.StoryQualityReader;
import com.kntro.reqsai.discovery.domain.model.StoryQualityAssessment;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.UserStoryResponse;
import com.kntro.reqsai.discovery.interfaces.rest.mappers.response.UserStoryResponseMapper;
import com.kntro.reqsai.discovery.interfaces.rest.swagger.SessionStoryController;
import com.kntro.reqsai.shared.interfaces.pagination.PageCriteria;
import com.kntro.reqsai.shared.interfaces.pagination.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** Implementation of the {@link SessionStoryController} API contract. */
@RestController
@RequiredArgsConstructor
public class SessionStoryControllerImpl implements SessionStoryController {

    private final GetSessionStoryQueryHandler getSessionStory;
    private final ListSessionStoriesQueryHandler listSessionStories;
    private final StoryQualityReader storyQuality;

    @Override
    @PreAuthorize("@discoveryAuthz.sessionPermission(#sessionId, 'STORY_READ', authentication)")
    public ResponseEntity<UserStoryResponse> getById(UUID sessionId, UUID storyId) {
        UserStory story = getSessionStory.handle(new GetSessionStoryQuery(sessionId, storyId));
        return ResponseEntity.ok(UserStoryResponseMapper.toResponse(story));
    }

    @Override
    @PreAuthorize("@discoveryAuthz.sessionPermission(#sessionId, 'STORY_READ', authentication)")
    public ResponseEntity<PageResponse<UserStoryResponse>> list(
            UUID sessionId, Integer page, Integer size, String sortBy, String sortDirection) {
        Page<UserStory> stories = listSessionStories.handle(new ListSessionStoriesQuery(
                sessionId, PageCriteria.of(page, size, sortBy, sortDirection)));
        Map<UUID, StoryQualityAssessment> quality = storyQuality.read(stories.getContent());
        return ResponseEntity.ok(PageResponse.of(
                stories.map(story -> UserStoryResponseMapper.toResponse(story, quality.get(story.getId())))));
    }
}
