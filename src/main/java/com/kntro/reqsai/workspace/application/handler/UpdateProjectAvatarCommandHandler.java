package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.UpdateProjectAvatarCommand;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replaces a project's avatar with an uploaded image. Scoped to the authenticated tenant the same way as
 * other project mutations: the tenant schema is bound by the JWT filter from the {@code orgId} claim, and
 * the project is resolved by id within that organization.
 */
@Component
@RequiredArgsConstructor
public class UpdateProjectAvatarCommandHandler {

    private final ProjectRepository projects;
    private final OrganizationRepository organizations;

    @Transactional
    public Project handle(UpdateProjectAvatarCommand command) {
        organizations.findById(command.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(command.organizationId()));

        Project project = projects.findByIdAndOrganizationIdAndStatus(
                        command.projectId(), command.organizationId(), ProjectStatus.ACTIVE)
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(command.projectId()));

        project.applyAvatar(command.bytes(), command.contentType());
        return projects.save(project);
    }
}
