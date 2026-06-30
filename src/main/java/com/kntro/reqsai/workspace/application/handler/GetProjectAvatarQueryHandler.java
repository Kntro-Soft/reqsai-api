package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.infrastructure.avatar.GeneratedAvatar;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.query.GetProjectAvatarQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Loads a project's stored avatar bytes for the public serve endpoint. Returns an empty {@link Optional}
 * when the project is unknown or has no avatar — the endpoint then responds {@code 404}.
 * <p>
 * The project lives in a tenant schema, so the caller must bind the tenant context (resolved from the
 * {@code organizationId} path variable) before invoking this read.
 */
@Component
@RequiredArgsConstructor
public class GetProjectAvatarQueryHandler {

    private final ProjectRepository projects;

    @Transactional(readOnly = true)
    public Optional<GeneratedAvatar> handle(GetProjectAvatarQuery query) {
        return projects.findByIdAndOrganizationId(query.projectId(), query.organizationId())
                .filter(project -> project.getAvatar() != null)
                .map(project -> new GeneratedAvatar(project.getAvatar(), project.getAvatarContentType()));
    }
}
