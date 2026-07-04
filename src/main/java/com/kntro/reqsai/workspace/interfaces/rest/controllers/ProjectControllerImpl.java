package com.kntro.reqsai.workspace.interfaces.rest.controllers;

import com.kntro.reqsai.workspace.application.command.ArchiveProjectCommand;
import com.kntro.reqsai.workspace.application.command.DeleteProjectCommand;
import com.kntro.reqsai.workspace.application.command.RestoreProjectCommand;
import com.kntro.reqsai.workspace.application.handler.ArchiveProjectCommandHandler;
import com.kntro.reqsai.workspace.application.handler.CreateProjectCommandHandler;
import com.kntro.reqsai.workspace.application.handler.DeleteProjectCommandHandler;
import com.kntro.reqsai.workspace.application.handler.GetProjectQueryHandler;
import com.kntro.reqsai.workspace.application.handler.ListProjectsQueryHandler;
import com.kntro.reqsai.workspace.application.handler.RestoreProjectCommandHandler;
import com.kntro.reqsai.workspace.application.handler.UpdateProjectCommandHandler;
import com.kntro.reqsai.workspace.application.query.GetProjectQuery;
import com.kntro.reqsai.workspace.application.query.ListProjectsQuery;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.CreateProjectRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.UpdateProjectRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.ProjectResponse;
import com.kntro.reqsai.workspace.interfaces.rest.mappers.request.ProjectRequestMapper;
import com.kntro.reqsai.workspace.interfaces.rest.mappers.response.ProjectResponseMapper;
import com.kntro.reqsai.workspace.interfaces.rest.swagger.ProjectController;
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
public class ProjectControllerImpl implements ProjectController {

    private final CreateProjectCommandHandler createProject;
    private final UpdateProjectCommandHandler updateProject;
    private final ArchiveProjectCommandHandler archiveProject;
    private final RestoreProjectCommandHandler restoreProject;
    private final DeleteProjectCommandHandler deleteProject;
    private final GetProjectQueryHandler getProject;
    private final ListProjectsQueryHandler listProjects;

    @Override
    @PreAuthorize("@authz.orgOwnerOrAdmin(#orgId, authentication)")
    public ResponseEntity<ProjectResponse> create(UUID orgId, CreateProjectRequest request, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        Project project = createProject.handle(ProjectRequestMapper.toCommand(orgId, request, requestedBy));
        
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(project.getId())
                .toUri();

        return ResponseEntity
            .created(location)
            .body(ProjectResponseMapper.toResponse(project));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#orgId, #projectId, 'PROJECT_UPDATE', authentication)")
    public ResponseEntity<ProjectResponse> update(UUID orgId, UUID projectId, UpdateProjectRequest request, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        Project project = updateProject.handle(ProjectRequestMapper.toCommand(orgId, projectId, request, requestedBy));
        return ResponseEntity.ok(ProjectResponseMapper.toResponse(project));
    }

    @Override
    @PreAuthorize("@authz.projectAccess(#orgId, #projectId, authentication)")
    public ResponseEntity<ProjectResponse> getById(UUID orgId, UUID projectId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        Project project = getProject.handle(new GetProjectQuery(orgId, projectId, requestedBy));
        return ResponseEntity.ok(ProjectResponseMapper.toResponse(project));
    }

    @Override
    @PreAuthorize("@authz.orgMember(#orgId, authentication)")
    public ResponseEntity<PageResponse<ProjectResponse>> list(UUID orgId, Integer page, Integer size, String sortBy, String sortDirection, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        PageResponse<ProjectResponse> response = PageResponse.of(
                listProjects.handle(new ListProjectsQuery(orgId, requestedBy, PageCriteria.of(page, size, sortBy, sortDirection)))
                        .map(ProjectResponseMapper::toResponse));
        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#orgId, #projectId, 'PROJECT_ARCHIVE', authentication)")
    public ResponseEntity<Void> archive(UUID orgId, UUID projectId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        archiveProject.handle(new ArchiveProjectCommand(orgId, projectId, requestedBy));
        return ResponseEntity.noContent().build();
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#orgId, #projectId, 'PROJECT_ARCHIVE', authentication)")
    public ResponseEntity<Void> restore(UUID orgId, UUID projectId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        restoreProject.handle(new RestoreProjectCommand(orgId, projectId, requestedBy));
        return ResponseEntity.noContent().build();
    }

    @Override
    @PreAuthorize("@authz.orgOwnerOrAdmin(#orgId, authentication)")
    public ResponseEntity<Void> delete(UUID orgId, UUID projectId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        deleteProject.handle(new DeleteProjectCommand(orgId, projectId, requestedBy));
        return ResponseEntity.noContent().build();
    }
}
