package com.kntro.reqsai.workspace.interfaces.rest.controllers;

import com.kntro.reqsai.workspace.application.command.AddProjectConstraintCommand;
import com.kntro.reqsai.workspace.application.handler.AddProjectConstraintCommandHandler;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.AddProjectConstraintRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.ProjectConstraintResponse;
import com.kntro.reqsai.workspace.interfaces.rest.mappers.response.ProjectConstraintResponseMapper;
import com.kntro.reqsai.workspace.interfaces.rest.swagger.ProjectConstraintController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ProjectConstraintControllerImpl implements ProjectConstraintController {

    private final AddProjectConstraintCommandHandler addConstraint;

    @Override
    public ResponseEntity<ProjectConstraintResponse> add(UUID orgId, UUID projectId,
                                                          AddProjectConstraintRequest request,
                                                          Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        var constraint = addConstraint.handle(new AddProjectConstraintCommand(projectId, request.description(), requestedBy));

        var location = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/api/v1/organizations/{orgId}/projects/{projectId}")
                .buildAndExpand(orgId, projectId)
                .toUri();

        return ResponseEntity.created(location).body(ProjectConstraintResponseMapper.toResponse(constraint));
    }
}
