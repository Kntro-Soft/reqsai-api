package com.kntro.reqsai.workspace.interfaces.rest.controllers;

import com.kntro.reqsai.workspace.application.service.ProjectSetupService;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.ProjectSetupResponse;
import com.kntro.reqsai.workspace.interfaces.rest.mappers.response.ProjectSetupResponseMapper;
import com.kntro.reqsai.workspace.interfaces.rest.swagger.ProjectSetupController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ProjectSetupControllerImpl implements ProjectSetupController {

    private final ProjectSetupService setup;

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'INTEGRATION_READ', authentication)")
    public ResponseEntity<ProjectSetupResponse> get(UUID projectId) {
        return ResponseEntity.ok(ProjectSetupResponseMapper.toResponse(setup.get(projectId)));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'INTEGRATION_WRITE', authentication)")
    public ResponseEntity<ProjectSetupResponse> skip(UUID projectId, String stepKey) {
        return ResponseEntity.ok(ProjectSetupResponseMapper.toResponse(setup.skip(projectId, stepKey)));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'INTEGRATION_WRITE', authentication)")
    public ResponseEntity<ProjectSetupResponse> retry(UUID projectId, String stepKey, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(ProjectSetupResponseMapper.toResponse(
                setup.retry(projectId, stepKey, requestedBy)));
    }
}
