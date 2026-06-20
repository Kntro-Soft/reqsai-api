package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Loads a single project, ensuring it belongs to the given organization. */
@Component
@RequiredArgsConstructor
public class GetProjectQueryHandler {

    private final ProjectRepository projects;

    @Transactional(readOnly = true)
    public Project handle(UUID organizationId, UUID projectId) {
        Project project = projects.findById(projectId)
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(projectId));
        if (!project.getOrganizationId().equals(organizationId)) {
            throw WorkspaceExceptions.projectNotFound(projectId);
        }
        return project;
    }
}
