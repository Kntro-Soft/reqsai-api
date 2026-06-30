package com.kntro.reqsai.workspace.interfaces.rest.controllers;

import com.kntro.reqsai.shared.interfaces.rest.AvatarResponses;
import com.kntro.reqsai.workspace.application.handler.GetProjectAvatarQueryHandler;
import com.kntro.reqsai.workspace.application.query.GetProjectAvatarQuery;
import com.kntro.reqsai.workspace.interfaces.rest.swagger.ProjectAvatarController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Implementation of the {@link ProjectAvatarController} API contract (public).
 * <p>
 * Projects live in a tenant schema; the application handler resolves the schema from {@code orgId} and
 * runs the read under the tenant context, so the controller stays free of infrastructure concerns.
 */
@RestController
@RequiredArgsConstructor
public class ProjectAvatarControllerImpl implements ProjectAvatarController {

    private final GetProjectAvatarQueryHandler getProjectAvatar;

    @Override
    public ResponseEntity<byte[]> getAvatar(UUID orgId, UUID projectId) {
        return AvatarResponses.of(getProjectAvatar.handle(new GetProjectAvatarQuery(orgId, projectId)));
    }
}
