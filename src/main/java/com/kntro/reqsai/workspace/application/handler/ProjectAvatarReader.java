package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.application.avatar.GeneratedAvatar;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.query.GetProjectAvatarQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Transactional read of a project's stored avatar bytes. Split out from
 * {@link GetProjectAvatarQueryHandler} so the {@code @Transactional} boundary is applied via the Spring
 * proxy (cross-bean call) and the session stays open while the lazy {@code avatar} blob is read. The
 * caller binds the tenant context before invoking this.
 */
@Component
@RequiredArgsConstructor
class ProjectAvatarReader {

    private final ProjectRepository projects;

    @Transactional(readOnly = true)
    public Optional<GeneratedAvatar> read(GetProjectAvatarQuery query) {
        return projects.findByIdAndOrganizationId(query.projectId(), query.organizationId())
                .filter(project -> project.getAvatar() != null)
                .map(project -> new GeneratedAvatar(project.getAvatar(), project.getAvatarContentType()));
    }
}
