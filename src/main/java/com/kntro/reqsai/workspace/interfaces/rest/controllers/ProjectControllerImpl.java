package com.kntro.reqsai.workspace.interfaces.rest.controllers;

import com.kntro.reqsai.workspace.application.command.DeleteProjectCommand;
import com.kntro.reqsai.workspace.application.handler.CreateProjectCommandHandler;
import com.kntro.reqsai.workspace.application.handler.DeleteProjectCommandHandler;
import com.kntro.reqsai.workspace.application.handler.UpdateProjectCommandHandler;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.CreateProjectRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.UpdateProjectRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.ProjectResponse;
import com.kntro.reqsai.workspace.interfaces.rest.mappers.request.ProjectRequestMapper;
import com.kntro.reqsai.workspace.interfaces.rest.mappers.response.ProjectResponseMapper;
import com.kntro.reqsai.workspace.interfaces.rest.swagger.ProjectController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    private final DeleteProjectCommandHandler deleteProject;

    @Override
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
    public ResponseEntity<ProjectResponse> update(UUID orgId, UUID projectId, UpdateProjectRequest request, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        Project project = updateProject.handle(ProjectRequestMapper.toCommand(orgId, projectId, request, requestedBy));
        return ResponseEntity.ok(ProjectResponseMapper.toResponse(project));
    }

    @Override
    public ResponseEntity<Void> delete(UUID orgId, UUID projectId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        deleteProject.handle(new DeleteProjectCommand(orgId, projectId, requestedBy));
        return ResponseEntity.noContent().build();
    }
}
