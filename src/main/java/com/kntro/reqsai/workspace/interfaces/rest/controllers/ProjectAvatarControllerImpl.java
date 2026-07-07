package com.kntro.reqsai.workspace.interfaces.rest.controllers;

import com.kntro.reqsai.shared.application.avatar.GeneratedAvatar;
import com.kntro.reqsai.shared.interfaces.rest.AvatarResponses;
import com.kntro.reqsai.shared.interfaces.rest.AvatarUploads;
import com.kntro.reqsai.workspace.application.command.UpdateProjectAvatarCommand;
import com.kntro.reqsai.workspace.application.handler.GetProjectAvatarQueryHandler;
import com.kntro.reqsai.workspace.application.handler.UpdateProjectAvatarCommandHandler;
import com.kntro.reqsai.workspace.application.query.GetProjectAvatarQuery;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.ProjectResponse;
import com.kntro.reqsai.workspace.interfaces.rest.mappers.response.ProjectResponseMapper;
import com.kntro.reqsai.workspace.interfaces.rest.swagger.ProjectAvatarController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Implementation of the {@link ProjectAvatarController} API contract (public GET, authenticated PUT).
 * <p>
 * Projects live in a tenant schema; the GET handler resolves the schema from {@code orgId} and runs the
 * read under the tenant context. The PUT upload runs inside the tenant schema bound by the JWT filter.
 */
@RestController
@RequiredArgsConstructor
public class ProjectAvatarControllerImpl implements ProjectAvatarController {

    private final GetProjectAvatarQueryHandler getProjectAvatar;
    private final UpdateProjectAvatarCommandHandler updateProjectAvatar;

    @Override
    public ResponseEntity<byte[]> getAvatar(UUID orgId, UUID projectId) {
        return AvatarResponses.of(getProjectAvatar.handle(new GetProjectAvatarQuery(orgId, projectId)));
    }

    @Override
    public ResponseEntity<ProjectResponse> uploadAvatar(UUID orgId, UUID projectId, MultipartFile file, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        GeneratedAvatar avatar = AvatarUploads.validated(file);
        Project project = updateProjectAvatar.handle(
                new UpdateProjectAvatarCommand(orgId, projectId, requestedBy, avatar.bytes(), avatar.contentType()));
        return ResponseEntity.ok(ProjectResponseMapper.toResponse(project));
    }
}
