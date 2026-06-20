package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.query.GetProjectConstraintQuery;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.model.ProjectConstraint;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetProjectConstraintQueryHandler {

    private final OrganizationRepository organizations;
    private final ProjectRepository projects;

    @Transactional(readOnly = true)
    public ProjectConstraint handle(GetProjectConstraintQuery query) {
        organizations.findById(query.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(query.organizationId()));

        Project project = projects.findByIdAndOrganizationIdAndStatus(
                        query.projectId(), query.organizationId(), ProjectStatus.ACTIVE)
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(query.projectId()));

        return project.getConstraint(query.constraintId());
    }
}
