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
import com.kntro.reqsai.shared.interfaces.pagination.PageCriteria;
import com.kntro.reqsai.shared.interfaces.pagination.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
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
    @PreAuthorize("@authz.projectPermission(#orgId, #projectId, 'CONSTRAINT_WRITE', authentication)")
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
    @PreAuthorize("@authz.projectPermission(#orgId, #projectId, 'CONSTRAINT_READ', authentication)")
    public ResponseEntity<PageResponse<ProjectConstraintResponse>> listConstraints(
            UUID orgId, UUID projectId, Integer page, Integer size, String search, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        PageResponse<ProjectConstraintResponse> response = PageResponse.of(
                listProjectConstraints.handle(new ListProjectConstraintsQuery(
                                orgId, projectId, requestedBy, PageCriteria.of(page, size, null, null), search))
                        .map(ProjectConstraintResponseMapper::toResponse));
        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#orgId, #projectId, 'CONSTRAINT_READ', authentication)")
    public ResponseEntity<ProjectConstraintResponse> getConstraint(UUID orgId, UUID projectId, UUID constraintId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        ProjectConstraint constraint = getProjectConstraint.handle(
                new GetProjectConstraintQuery(orgId, projectId, constraintId, requestedBy));
        return ResponseEntity.ok(ProjectConstraintResponseMapper.toResponse(constraint));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#orgId, #projectId, 'CONSTRAINT_WRITE', authentication)")
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
    @PreAuthorize("@authz.projectPermission(#orgId, #projectId, 'CONSTRAINT_WRITE', authentication)")
    public ResponseEntity<Void> deleteConstraint(UUID orgId, UUID projectId, UUID constraintId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        deleteProjectConstraint.handle(
                ProjectConstraintRequestMapper.toDeleteCommand(orgId, projectId, constraintId, requestedBy));
        return ResponseEntity.noContent().build();
    }
}
