package com.kntro.reqsai.workspace.interfaces.rest.controllers;

import com.kntro.reqsai.workspace.application.handler.*;
import com.kntro.reqsai.workspace.application.query.GetProjectRoleQuery;
import com.kntro.reqsai.workspace.application.query.ListProjectRolesQuery;
import com.kntro.reqsai.workspace.domain.model.ProjectRole;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.CreateProjectRoleRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.UpdateProjectRoleRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.ProjectRoleResponse;
import com.kntro.reqsai.workspace.interfaces.rest.mappers.request.ProjectRoleRequestMapper;
import com.kntro.reqsai.workspace.interfaces.rest.mappers.response.ProjectRoleResponseMapper;
import com.kntro.reqsai.workspace.interfaces.rest.swagger.ProjectRoleController;
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
public class ProjectRoleControllerImpl implements ProjectRoleController {

    private final CreateProjectRoleCommandHandler createRole;
    private final ListProjectRolesQueryHandler listRoles;
    private final GetProjectRoleQueryHandler getRole;
    private final UpdateProjectRoleCommandHandler updateRole;
    private final DeleteProjectRoleCommandHandler deleteRole;

    @Override
    @PreAuthorize("@authz.projectPermission(#orgId, #projectId, 'ROLE_CREATE', authentication)")
    public ResponseEntity<ProjectRoleResponse> createRole(UUID orgId, UUID projectId, CreateProjectRoleRequest request, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        ProjectRole role = createRole.handle(ProjectRoleRequestMapper.toCommand(orgId, projectId, request, requestedBy));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(role.getId()).toUri();
        return ResponseEntity.created(location).body(ProjectRoleResponseMapper.toResponse(role));
    }

    @Override
    @PreAuthorize("@authz.projectAnyPermission(#orgId, #projectId, authentication, 'ROLE_READ', 'MEMBER_UPDATE_ROLE', 'MEMBER_INVITE')")
    public ResponseEntity<List<ProjectRoleResponse>> listRoles(UUID orgId, UUID projectId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(listRoles.handle(new ListProjectRolesQuery(orgId, projectId, requestedBy)).stream()
                .map(ProjectRoleResponseMapper::toResponse).toList());
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#orgId, #projectId, 'ROLE_READ', authentication)")
    public ResponseEntity<ProjectRoleResponse> getRole(UUID orgId, UUID projectId, UUID roleId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(ProjectRoleResponseMapper.toResponse(getRole.handle(new GetProjectRoleQuery(orgId, projectId, roleId, requestedBy))));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#orgId, #projectId, 'ROLE_UPDATE', authentication)")
    public ResponseEntity<ProjectRoleResponse> updateRole(UUID orgId, UUID projectId, UUID roleId, UpdateProjectRoleRequest request, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        ProjectRole role = updateRole.handle(ProjectRoleRequestMapper.toCommand(orgId, projectId, roleId, request, requestedBy));
        return ResponseEntity.ok(ProjectRoleResponseMapper.toResponse(role));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#orgId, #projectId, 'ROLE_DELETE', authentication)")
    public ResponseEntity<Void> deleteRole(UUID orgId, UUID projectId, UUID roleId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        deleteRole.handle(ProjectRoleRequestMapper.toDeleteCommand(orgId, projectId, roleId, requestedBy));
        return ResponseEntity.noContent().build();
    }
}
