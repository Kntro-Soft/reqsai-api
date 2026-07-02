package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.port.ProjectMemberRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.query.GetProjectMemberQuery;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.ProjectMember;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetProjectMemberQueryHandler {

    private final ProjectRepository projects;
    private final ProjectMemberRepository assignments;

    @Transactional(readOnly = true)
    public ProjectMember handle(GetProjectMemberQuery query) {
        projects.findByIdAndOrganizationIdAndStatus(query.projectId(), query.organizationId(), ProjectStatus.ACTIVE)
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(query.projectId()));

        return assignments.findByIdAndProjectId(query.assignmentId(), query.projectId())
                .orElseThrow(() -> WorkspaceExceptions.projectMemberNotFound(query.assignmentId()));
    }
}
