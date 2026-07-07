package com.kntro.reqsai.discovery.interfaces.rest.controllers;

import com.kntro.reqsai.discovery.application.handler.CreateUserStoryCommandHandler;
import com.kntro.reqsai.discovery.application.handler.GetProjectStoryQueryHandler;
import com.kntro.reqsai.discovery.application.handler.ListProjectStoriesQueryHandler;
import com.kntro.reqsai.discovery.application.handler.UpdateUserStoryCommandHandler;
import com.kntro.reqsai.discovery.application.query.GetProjectStoryQuery;
import com.kntro.reqsai.discovery.application.query.ListProjectStoriesQuery;
import com.kntro.reqsai.discovery.application.query.StoryFilter;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.StoryStatus;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.discovery.interfaces.rest.dto.request.CreateUserStoryRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.request.UpdateUserStoryRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.UserStoryResponse;
import com.kntro.reqsai.discovery.interfaces.rest.mappers.request.UserStoryRequestMapper;
import com.kntro.reqsai.discovery.interfaces.rest.mappers.response.UserStoryResponseMapper;
import com.kntro.reqsai.discovery.interfaces.rest.swagger.ProjectStoryController;
import com.kntro.reqsai.shared.interfaces.pagination.PageCriteria;
import com.kntro.reqsai.shared.interfaces.pagination.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/** Implementation of the {@link ProjectStoryController} API contract. */
@RestController
@RequiredArgsConstructor
public class ProjectStoryControllerImpl implements ProjectStoryController {

    private final CreateUserStoryCommandHandler createUserStory;
    private final GetProjectStoryQueryHandler getUserStory;
    private final ListProjectStoriesQueryHandler listUserStories;
    private final UpdateUserStoryCommandHandler updateUserStory;

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'STORY_WRITE', authentication)")
    public ResponseEntity<UserStoryResponse> create(UUID projectId, CreateUserStoryRequest request) {
        UserStory story = createUserStory.handle(UserStoryRequestMapper.toCommand(projectId, request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(story.getId())
                .toUri();
        return ResponseEntity.created(location).body(UserStoryResponseMapper.toResponse(story));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'STORY_READ', authentication)")
    public ResponseEntity<UserStoryResponse> getById(UUID projectId, UUID storyId) {
        UserStory story = getUserStory.handle(new GetProjectStoryQuery(projectId, storyId));
        return ResponseEntity.ok(UserStoryResponseMapper.toResponse(story));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'STORY_READ', authentication)")
    public ResponseEntity<PageResponse<UserStoryResponse>> list(
            UUID projectId, Integer page, Integer size, String sortBy, String sortDirection,
            String search, String status, String priority, Instant createdAfter, Instant createdBefore) {
        StoryFilter filter = new StoryFilter(
                search,
                parseEnum(StoryStatus.class, status, "status"),
                parseEnum(Priority.class, priority, "priority"),
                createdAfter,
                createdBefore);
        PageResponse<UserStoryResponse> response = PageResponse.of(
                listUserStories.handle(new ListProjectStoriesQuery(
                        projectId, PageCriteria.of(page, size, sortBy, sortDirection), filter))
                        .map(UserStoryResponseMapper::toResponse));
        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'STORY_WRITE', authentication)")
    public ResponseEntity<UserStoryResponse> update(UUID projectId, UUID storyId, UpdateUserStoryRequest request) {
        UserStory story = updateUserStory.handle(
                UserStoryRequestMapper.toUpdateCommand(projectId, storyId, request));
        return ResponseEntity.ok(UserStoryResponseMapper.toResponse(story));
    }

    /**
     * Parses an optional enum query param, treating {@code null}/blank as "no filter". An unrecognized
     * value is a client error → 400, rather than being silently dropped (which would return an
     * unexpectedly unfiltered page).
     */
    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String paramName) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Invalid '%s' value: %s".formatted(paramName, raw));
        }
    }
}
