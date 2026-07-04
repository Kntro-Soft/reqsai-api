package com.kntro.reqsai.workspace.interfaces.rest.controllers;

import com.kntro.reqsai.workspace.application.handler.*;
import com.kntro.reqsai.workspace.application.query.GetProjectMemberQuery;
import com.kntro.reqsai.workspace.application.query.ListProjectMembersQuery;
import com.kntro.reqsai.workspace.domain.model.ProjectMember;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.CreateProjectMemberRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.UpdateProjectMemberRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.ProjectMemberResponse;
import com.kntro.reqsai.workspace.interfaces.rest.mappers.request.ProjectMemberRequestMapper;
import com.kntro.reqsai.workspace.interfaces.rest.mappers.response.ProjectMemberResponseMapper;
import com.kntro.reqsai.workspace.interfaces.rest.swagger.ProjectMemberController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ProjectMemberControllerImpl implements ProjectMemberController {

    private final CreateProjectMemberCommandHandler createAssignment;
    private final ListProjectMembersQueryHandler listAssignments;
    private final GetProjectMemberQueryHandler getAssignment;
    private final UpdateProjectMemberCommandHandler updateAssignment;
    private final DeleteProjectMemberCommandHandler deleteAssignment;

    @Override
    @PreAuthorize("@authz.projectPermission(#orgId, #projectId, 'MEMBER_INVITE', authentication)")
    public ResponseEntity<ProjectMemberResponse> createAssignment(UUID orgId, UUID projectId, CreateProjectMemberRequest request, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        ProjectMember assignment = createAssignment.handle(ProjectMemberRequestMapper.toCommand(orgId, projectId, request, requestedBy));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(assignment.getId()).toUri();
        return ResponseEntity.created(location).body(ProjectMemberResponseMapper.toResponse(assignment));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#orgId, #projectId, 'MEMBER_READ', authentication)")
    public ResponseEntity<List<ProjectMemberResponse>> listAssignments(UUID orgId, UUID projectId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(listAssignments.handle(new ListProjectMembersQuery(orgId, projectId, requestedBy)).stream()
                .map(ProjectMemberResponseMapper::toResponse).toList());
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#orgId, #projectId, 'MEMBER_READ', authentication)")
    public ResponseEntity<ProjectMemberResponse> getAssignment(UUID orgId, UUID projectId, UUID assignmentId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(ProjectMemberResponseMapper.toResponse(getAssignment.handle(new GetProjectMemberQuery(orgId, projectId, assignmentId, requestedBy))));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#orgId, #projectId, 'MEMBER_UPDATE_ROLE', authentication)")
    public ResponseEntity<ProjectMemberResponse> updateAssignment(UUID orgId, UUID projectId, UUID assignmentId, UpdateProjectMemberRequest request, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        ProjectMember assignment = updateAssignment.handle(ProjectMemberRequestMapper.toCommand(orgId, projectId, assignmentId, request, requestedBy));
        return ResponseEntity.ok(ProjectMemberResponseMapper.toResponse(assignment));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#orgId, #projectId, 'MEMBER_REMOVE', authentication)")
    public ResponseEntity<Void> deleteAssignment(UUID orgId, UUID projectId, UUID assignmentId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        deleteAssignment.handle(ProjectMemberRequestMapper.toDeleteCommand(orgId, projectId, assignmentId, requestedBy));
        return ResponseEntity.noContent().build();
    }
}
