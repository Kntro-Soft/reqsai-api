package com.kntro.reqsai.workspace.interfaces.rest.controllers;

import com.kntro.reqsai.workspace.application.handler.DeleteProjectConstraintCommandHandler;
import com.kntro.reqsai.workspace.application.handler.AddProjectConstraintCommandHandler;
import com.kntro.reqsai.workspace.application.handler.GetProjectConstraintQueryHandler;
import com.kntro.reqsai.workspace.application.handler.ListProjectConstraintsQueryHandler;
import com.kntro.reqsai.workspace.application.handler.UpdateProjectConstraintCommandHandler;
import com.kntro.reqsai.workspace.application.query.GetProjectConstraintQuery;
import com.kntro.reqsai.workspace.application.query.ListProjectConstraintsQuery;
import com.kntro.reqsai.workspace.domain.model.ProjectConstraint;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.AddProjectConstraintRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.UpdateProjectConstraintRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.ProjectConstraintResponse;
import com.kntro.reqsai.workspace.interfaces.rest.mappers.request.ProjectConstraintRequestMapper;
import com.kntro.reqsai.workspace.interfaces.rest.mappers.response.ProjectConstraintResponseMapper;
import com.kntro.reqsai.workspace.interfaces.rest.swagger.ProjectConstraintController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ProjectConstraintControllerImpl implements ProjectConstraintController {

    private final AddProjectConstraintCommandHandler addProjectConstraint;
    private final ListProjectConstraintsQueryHandler listProjectConstraints;
    private final GetProjectConstraintQueryHandler getProjectConstraint;
    private final UpdateProjectConstraintCommandHandler updateProjectConstraint;
    private final DeleteProjectConstraintCommandHandler deleteProjectConstraint;

    @Override
    public ResponseEntity<ProjectConstraintResponse> addConstraint(
            UUID orgId,
            UUID projectId,
            AddProjectConstraintRequest request,
            Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        ProjectConstraint constraint = addProjectConstraint.handle(
                ProjectConstraintRequestMapper.toCommand(orgId, projectId, request, requestedBy));

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(constraint.getId())
                .toUri();

        return ResponseEntity.created(location)
                .body(ProjectConstraintResponseMapper.toResponse(constraint));
    }

    @Override
    public ResponseEntity<List<ProjectConstraintResponse>> listConstraints(UUID orgId, UUID projectId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        List<ProjectConstraintResponse> response = listProjectConstraints.handle(
                        new ListProjectConstraintsQuery(orgId, projectId, requestedBy))
                .stream()
                .map(ProjectConstraintResponseMapper::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ProjectConstraintResponse> getConstraint(UUID orgId, UUID projectId, UUID constraintId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        ProjectConstraint constraint = getProjectConstraint.handle(
                new GetProjectConstraintQuery(orgId, projectId, constraintId, requestedBy));
        return ResponseEntity.ok(ProjectConstraintResponseMapper.toResponse(constraint));
    }

    @Override
    public ResponseEntity<ProjectConstraintResponse> updateConstraint(
            UUID orgId,
            UUID projectId,
            UUID constraintId,
            UpdateProjectConstraintRequest request,
            Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        ProjectConstraint constraint = updateProjectConstraint.handle(
                ProjectConstraintRequestMapper.toCommand(orgId, projectId, constraintId, request, requestedBy));
        return ResponseEntity.ok(ProjectConstraintResponseMapper.toResponse(constraint));
    }

    @Override
    public ResponseEntity<Void> deleteConstraint(UUID orgId, UUID projectId, UUID constraintId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        deleteProjectConstraint.handle(
                ProjectConstraintRequestMapper.toDeleteCommand(orgId, projectId, constraintId, requestedBy));
        return ResponseEntity.noContent().build();
    }
}
