package com.kntro.reqsai.workspace.interfaces.rest.controllers;

import com.kntro.reqsai.workspace.application.handler.GetMyProjectPermissionsQueryHandler;
import com.kntro.reqsai.workspace.application.query.GetMyProjectPermissionsQuery;
import com.kntro.reqsai.workspace.domain.model.Permission;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.MyProjectPermissionsResponse;
import com.kntro.reqsai.workspace.interfaces.rest.swagger.ProjectPermissionsController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

/** Implementation of the {@link ProjectPermissionsController} API contract. */
@RestController
@RequiredArgsConstructor
public class ProjectPermissionsControllerImpl implements ProjectPermissionsController {

    private final GetMyProjectPermissionsQueryHandler getMyPermissions;

    @Override
    @PreAuthorize("@authz.tenantMember(authentication)")
    public ResponseEntity<MyProjectPermissionsResponse> getMyPermissions(
            UUID projectId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        Set<Permission> permissions = getMyPermissions.handle(
                new GetMyProjectPermissionsQuery(projectId, requestedBy));
        return ResponseEntity.ok(new MyProjectPermissionsResponse(permissions));
    }
}
