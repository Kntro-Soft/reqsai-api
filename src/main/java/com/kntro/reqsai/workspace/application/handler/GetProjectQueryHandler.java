package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.query.GetProjectQuery;
import com.kntro.reqsai.workspace.application.service.ProjectAccessService;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetProjectQueryHandler {

    private final ProjectRepository projects;
    private final OrganizationRepository organizations;
    private final ProjectAccessService projectAccess;

    @Transactional(readOnly = true)
    public Project handle(GetProjectQuery query) {
        Organization organization = organizations.findById(query.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(query.organizationId()));

        Project project = projects.findByIdAndOrganizationIdAndStatus(
                        query.projectId(), query.organizationId(), ProjectStatus.ACTIVE)
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(query.projectId()));

        // Owners/admins access any project; a regular member must hold an explicit assignment.
        projectAccess.assertCanAccessProject(organization, query.projectId(), query.requestedBy());
        return project;
    }
}
