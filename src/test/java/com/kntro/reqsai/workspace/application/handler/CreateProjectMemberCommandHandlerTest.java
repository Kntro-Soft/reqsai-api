package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.command.CreateProjectMemberCommand;
import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.application.port.ProjectMemberRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRoleRepository;
import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import com.kntro.reqsai.workspace.domain.model.OrgRole;
import com.kntro.reqsai.workspace.domain.model.Permission;
import com.kntro.reqsai.workspace.domain.model.ProjectMember;
import com.kntro.reqsai.workspace.domain.model.ProjectRole;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import com.kntro.reqsai.workspace.mothers.ProjectMother;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Application: Create Project Member")
@ExtendWith(MockitoExtension.class)
class CreateProjectMemberCommandHandlerTest {

    @Mock
    private ProjectRepository projects;
    @Mock
    private MemberRepository members;
    @Mock
    private ProjectRoleRepository roles;
    @Mock
    private ProjectMemberRepository assignments;
    @InjectMocks
    private CreateProjectMemberCommandHandler handler;

    @Nested
    @DisplayName("Successful creation")
    class SuccessfulCreation {

        @Test
        @DisplayName("should assign active member to role from same project")
        void should_assign_active_member_to_role_from_same_project() {
            UUID orgId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            UUID memberId = UUID.randomUUID();
            UUID roleId = UUID.randomUUID();
            UUID requestedBy = UUID.randomUUID();

            CreateProjectMemberCommand command = new CreateProjectMemberCommand(orgId, projectId, memberId, roleId, requestedBy);

            when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ACTIVE))
                    .thenReturn(Optional.of(ProjectMother.standard().withOrganizationId(orgId).build()));
            when(members.findByIdAndOrganizationIdAndStatusIn(memberId, orgId, List.of(MemberStatus.ACTIVE)))
                    .thenReturn(Optional.of(new Member(orgId, UUID.randomUUID(), "member@example.com", "Member", OrgRole.MEMBER,
                            MemberStatus.ACTIVE, requestedBy, Instant.now())));
            when(roles.findByIdAndProjectId(roleId, projectId))
                    .thenReturn(Optional.of(new ProjectRole(projectId, "Analyst", Set.of(Permission.MEMBER_READ))));
            when(assignments.existsByProjectIdAndMemberId(projectId, memberId)).thenReturn(false);
            when(assignments.save(any(ProjectMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ProjectMember assignment = handler.handle(command);

            assertThat(assignment.getMemberId()).isEqualTo(memberId);
            assertThat(assignment.getRoleId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Validation failures")
    class ValidationFailures {

        @Test
        @DisplayName("should fail if member is not active")
        void should_fail_if_member_is_not_active() {
            UUID orgId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            UUID memberId = UUID.randomUUID();
            UUID roleId = UUID.randomUUID();
            UUID requestedBy = UUID.randomUUID();
            CreateProjectMemberCommand command = new CreateProjectMemberCommand(orgId, projectId, memberId, roleId, requestedBy);

            when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ACTIVE))
                    .thenReturn(Optional.of(ProjectMother.standard().withOrganizationId(orgId).build()));
            when(members.findByIdAndOrganizationIdAndStatusIn(memberId, orgId, List.of(MemberStatus.ACTIVE)))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> handler.handle(command)).isInstanceOf(DomainException.class);
            verify(assignments, never()).save(any());
        }

        @Test
        @DisplayName("should fail if role belongs to another project or does not exist")
        void should_fail_if_role_belongs_to_another_project_or_does_not_exist() {
            UUID orgId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            UUID memberId = UUID.randomUUID();
            UUID roleId = UUID.randomUUID();
            UUID requestedBy = UUID.randomUUID();
            CreateProjectMemberCommand command = new CreateProjectMemberCommand(orgId, projectId, memberId, roleId, requestedBy);

            when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ACTIVE))
                    .thenReturn(Optional.of(ProjectMother.standard().withOrganizationId(orgId).build()));
            when(members.findByIdAndOrganizationIdAndStatusIn(memberId, orgId, List.of(MemberStatus.ACTIVE)))
                    .thenReturn(Optional.of(new Member(orgId, UUID.randomUUID(), "member@example.com", "Member", OrgRole.MEMBER,
                            MemberStatus.ACTIVE, requestedBy, Instant.now())));
            when(roles.findByIdAndProjectId(roleId, projectId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> handler.handle(command)).isInstanceOf(DomainException.class);
            verify(assignments, never()).save(any());
        }
    }
}
