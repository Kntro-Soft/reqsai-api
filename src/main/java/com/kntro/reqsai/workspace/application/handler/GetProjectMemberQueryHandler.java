package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectMemberRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.query.GetProjectMemberQuery;
import com.kntro.reqsai.workspace.application.service.OrganizationAdminAccessService;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.ProjectMember;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetProjectMemberQueryHandler {

    private final OrganizationRepository organizations;
    private final ProjectRepository projects;
    private final ProjectMemberRepository assignments;
    private final OrganizationAdminAccessService access;

    @Transactional(readOnly = true)
    public ProjectMember handle(GetProjectMemberQuery query) {
        Organization organization = organizations.findById(query.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(query.organizationId()));
        access.assertOwnerOrAdmin(organization, query.requestedBy(), "view project members");

        projects.findByIdAndOrganizationIdAndStatus(query.projectId(), query.organizationId(), ProjectStatus.ACTIVE)
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(query.projectId()));

        return assignments.findByIdAndProjectId(query.assignmentId(), query.projectId())
                .orElseThrow(() -> WorkspaceExceptions.projectMemberNotFound(query.assignmentId()));
    }
}
