package com.kntro.reqsai.workspace.interfaces.rest.controllers;

import com.kntro.reqsai.workspace.application.handler.CreateProjectCommandHandler;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.CreateProjectRequest;
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
}
