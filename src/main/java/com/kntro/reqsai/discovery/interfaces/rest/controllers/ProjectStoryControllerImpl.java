package com.kntro.reqsai.discovery.interfaces.rest.controllers;

import com.kntro.reqsai.discovery.application.handler.CreateUserStoryCommandHandler;
import com.kntro.reqsai.discovery.application.handler.GetProjectStoryQueryHandler;
import com.kntro.reqsai.discovery.application.handler.ListProjectStoriesQueryHandler;
import com.kntro.reqsai.discovery.application.query.GetProjectStoryQuery;
import com.kntro.reqsai.discovery.application.query.ListProjectStoriesQuery;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.discovery.interfaces.rest.dto.request.CreateUserStoryRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.UserStoryResponse;
import com.kntro.reqsai.discovery.interfaces.rest.mappers.request.UserStoryRequestMapper;
import com.kntro.reqsai.discovery.interfaces.rest.mappers.response.UserStoryResponseMapper;
import com.kntro.reqsai.discovery.interfaces.rest.swagger.ProjectStoryController;
import com.kntro.reqsai.shared.interfaces.pagination.PageCriteria;
import com.kntro.reqsai.shared.interfaces.pagination.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/** Implementation of the {@link ProjectStoryController} API contract. */
@RestController
@RequiredArgsConstructor
public class ProjectStoryControllerImpl implements ProjectStoryController {

    private final CreateUserStoryCommandHandler createUserStory;
    private final GetProjectStoryQueryHandler getUserStory;
    private final ListProjectStoriesQueryHandler listUserStories;

    @Override
    public ResponseEntity<UserStoryResponse> create(UUID projectId, CreateUserStoryRequest request) {
        UserStory story = createUserStory.handle(UserStoryRequestMapper.toCommand(projectId, request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(story.getId())
                .toUri();
        return ResponseEntity.created(location).body(UserStoryResponseMapper.toResponse(story));
    }

    @Override
    public ResponseEntity<UserStoryResponse> getById(UUID projectId, UUID storyId) {
        UserStory story = getUserStory.handle(new GetProjectStoryQuery(projectId, storyId));
        return ResponseEntity.ok(UserStoryResponseMapper.toResponse(story));
    }

    @Override
    public ResponseEntity<PageResponse<UserStoryResponse>> list(
            UUID projectId, Integer page, Integer size, String sortBy, String sortDirection) {
        PageResponse<UserStoryResponse> response = PageResponse.of(
                listUserStories.handle(new ListProjectStoriesQuery(
                        projectId, PageCriteria.of(page, size, sortBy, sortDirection)))
                        .map(UserStoryResponseMapper::toResponse));
        return ResponseEntity.ok(response);
    }
}
