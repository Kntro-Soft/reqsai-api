package com.kntro.reqsai.discovery.interfaces.rest.controllers;

import com.kntro.reqsai.discovery.application.handler.CreateUserStoryCommandHandler;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.discovery.interfaces.rest.dto.request.CreateUserStoryRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.UserStoryResponse;
import com.kntro.reqsai.discovery.interfaces.rest.mappers.request.UserStoryRequestMapper;
import com.kntro.reqsai.discovery.interfaces.rest.mappers.response.UserStoryResponseMapper;
import com.kntro.reqsai.discovery.interfaces.rest.swagger.ProjectStoryController;
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

    @Override
    public ResponseEntity<UserStoryResponse> create(UUID projectId, CreateUserStoryRequest request) {
        UserStory story = createUserStory.handle(UserStoryRequestMapper.toCommand(projectId, request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(story.getId())
                .toUri();
        return ResponseEntity.created(location).body(UserStoryResponseMapper.toResponse(story));
    }
}
