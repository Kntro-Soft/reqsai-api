package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRoleRepository;
import com.kntro.reqsai.workspace.application.query.GetProjectRoleQuery;
import com.kntro.reqsai.workspace.application.service.OrganizationAdminAccessService;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.ProjectRole;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetProjectRoleQueryHandler {

    private final OrganizationRepository organizations;
    private final ProjectRepository projects;
    private final ProjectRoleRepository roles;
    private final OrganizationAdminAccessService access;

    @Transactional(readOnly = true)
    public ProjectRole handle(GetProjectRoleQuery query) {
        Organization organization = organizations.findById(query.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(query.organizationId()));
        access.assertOwnerOrAdmin(organization, query.requestedBy(), "view project roles");

        projects.findByIdAndOrganizationIdAndStatus(query.projectId(), query.organizationId(), ProjectStatus.ACTIVE)
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(query.projectId()));

        return roles.findByIdAndProjectId(query.roleId(), query.projectId())
                .orElseThrow(() -> WorkspaceExceptions.projectRoleNotFound(query.roleId()));
    }
}
