package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.port.ProjectMemberRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRoleRepository;
import com.kntro.reqsai.workspace.application.query.ListProjectMembersQuery;
import com.kntro.reqsai.workspace.application.result.ProjectMemberAssignment;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.ProjectRole;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ListProjectMembersQueryHandler {

    private final ProjectRepository projects;
    private final ProjectMemberRepository assignments;
    private final ProjectRoleRepository roles;

    @Transactional(readOnly = true)
    public List<ProjectMemberAssignment> handle(ListProjectMembersQuery query) {
        projects.findByIdAndOrganizationIdAndStatus(query.projectId(), query.organizationId(), ProjectStatus.ACTIVE)
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(query.projectId()));

        // Resolve role names once here so the members list carries them: viewing members
        // (MEMBER_READ) shouldn't also require ROLE_READ just to display each member's role.
        Map<UUID, String> roleNames = roles.findAllByProjectId(query.projectId()).stream()
                .collect(Collectors.toMap(ProjectRole::getId, ProjectRole::getName));

        return assignments.findAllByProjectId(query.projectId()).stream()
                .map(assignment -> new ProjectMemberAssignment(
                        assignment, roleNames.get(assignment.getRoleId())))
                .toList();
    }
}
