package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.query.GetProjectQuery;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetProjectQueryHandler {

    private final ProjectRepository projects;
    private final OrganizationRepository organizations;

    @Transactional(readOnly = true)
    public Project handle(GetProjectQuery query) {
        organizations.findById(query.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(query.organizationId()));

        Project project = projects.findById(query.projectId())
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(query.projectId()));
        if (!project.getOrganizationId().equals(query.organizationId())) {
            throw WorkspaceExceptions.projectNotFound(query.projectId());
        }
        return project;
    }
}
