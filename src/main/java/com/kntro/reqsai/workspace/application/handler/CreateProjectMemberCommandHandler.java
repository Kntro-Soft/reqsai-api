package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.CreateProjectMemberCommand;
import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectMemberRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRoleRepository;
import com.kntro.reqsai.workspace.application.service.OrganizationAdminAccessService;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.ProjectMember;
import com.kntro.reqsai.workspace.domain.model.ProjectRole;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CreateProjectMemberCommandHandler {

    private final OrganizationRepository organizations;
    private final ProjectRepository projects;
    private final MemberRepository members;
    private final ProjectRoleRepository roles;
    private final ProjectMemberRepository assignments;
    private final OrganizationAdminAccessService access;

    @Transactional
    public ProjectMember handle(CreateProjectMemberCommand command) {
        Organization organization = organizations.findById(command.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(command.organizationId()));
        access.assertOwnerOrAdmin(organization, command.requestedBy(), "manage project members");

        projects.findByIdAndOrganizationIdAndStatus(command.projectId(), command.organizationId(), ProjectStatus.ACTIVE)
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(command.projectId()));

        Member member = members.findByIdAndOrganizationIdAndStatusIn(command.memberId(), command.organizationId(),
                        List.of(MemberStatus.ACTIVE))
                .orElseThrow(() -> WorkspaceExceptions.memberNotFound(command.memberId()));

        ProjectRole role = roles.findByIdAndProjectId(command.roleId(), command.projectId())
                .orElseThrow(() -> WorkspaceExceptions.projectRoleNotFound(command.roleId()));

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw WorkspaceExceptions.memberNotFound(command.memberId());
        }
        if (assignments.existsByProjectIdAndMemberId(command.projectId(), command.memberId())) {
            throw WorkspaceExceptions.projectMemberAlreadyExists(command.memberId());
        }

        return assignments.save(new ProjectMember(
                command.projectId(), command.memberId(), role.getId(), command.requestedBy(), Instant.now()));
    }
}
