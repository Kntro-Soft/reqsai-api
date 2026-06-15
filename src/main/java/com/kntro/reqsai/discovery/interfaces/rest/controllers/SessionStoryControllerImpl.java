package com.kntro.reqsai.discovery.interfaces.rest.controllers;

import com.kntro.reqsai.discovery.application.handler.GetSessionStoryQueryHandler;
import com.kntro.reqsai.discovery.application.handler.ListSessionStoriesQueryHandler;
import com.kntro.reqsai.discovery.application.query.GetSessionStoryQuery;
import com.kntro.reqsai.discovery.application.query.ListSessionStoriesQuery;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.UserStoryResponse;
import com.kntro.reqsai.discovery.interfaces.rest.mappers.response.UserStoryResponseMapper;
import com.kntro.reqsai.discovery.interfaces.rest.swagger.SessionStoryController;
import com.kntro.reqsai.shared.interfaces.pagination.PageCriteria;
import com.kntro.reqsai.shared.interfaces.pagination.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Implementation of the {@link SessionStoryController} API contract. */
@RestController
@RequiredArgsConstructor
public class SessionStoryControllerImpl implements SessionStoryController {

    private final GetSessionStoryQueryHandler getSessionStory;
    private final ListSessionStoriesQueryHandler listSessionStories;

    @Override
    public ResponseEntity<UserStoryResponse> getById(UUID sessionId, UUID storyId) {
        UserStory story = getSessionStory.handle(new GetSessionStoryQuery(sessionId, storyId));
        return ResponseEntity.ok(UserStoryResponseMapper.toResponse(story));
    }

    @Override
    public ResponseEntity<PageResponse<UserStoryResponse>> list(
            UUID sessionId, Integer page, Integer size, String sortBy, String sortDirection) {
        PageResponse<UserStoryResponse> response = PageResponse.of(
                listSessionStories.handle(new ListSessionStoriesQuery(
                        sessionId, PageCriteria.of(page, size, sortBy, sortDirection)))
                        .map(UserStoryResponseMapper::toResponse));
        return ResponseEntity.ok(response);
    }
}
