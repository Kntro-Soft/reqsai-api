package com.kntro.reqsai.discovery.interfaces.rest.controllers;

import com.kntro.reqsai.discovery.application.command.AddAcceptanceCriterionCommand;
import com.kntro.reqsai.discovery.application.handler.AddAcceptanceCriterionCommandHandler;
import com.kntro.reqsai.discovery.domain.model.AcceptanceCriterion;
import com.kntro.reqsai.discovery.interfaces.rest.dto.request.AddAcceptanceCriterionRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.AcceptanceCriterionResponse;
import com.kntro.reqsai.discovery.interfaces.rest.mappers.response.AcceptanceCriterionResponseMapper;
import com.kntro.reqsai.discovery.interfaces.rest.swagger.StoryAcceptanceCriteriaController;
import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class StoryAcceptanceCriteriaControllerImpl implements StoryAcceptanceCriteriaController {

    private final AddAcceptanceCriterionCommandHandler addCriterion;

    @Override
    public ResponseEntity<AcceptanceCriterionResponse> add(UUID projectId, UUID storyId, AddAcceptanceCriterionRequest request) {
        AcceptanceCriterion criterion = addCriterion.handle(
                new AddAcceptanceCriterionCommand(projectId, storyId, request.scenario(), request.given(), request.when(), request.then()));
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(ApiVersioning.BASE + "/projects/{projectId}/stories/{storyId}")
                .buildAndExpand(projectId, storyId)
                .toUri();
        return ResponseEntity.created(location).body(AcceptanceCriterionResponseMapper.toResponse(criterion));
    }
}
