package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.command.DeleteProjectRoleCommand;
import com.kntro.reqsai.workspace.application.port.ProjectMemberRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRoleRepository;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceError;
import com.kntro.reqsai.workspace.domain.model.Permission;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.model.ProjectRole;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import com.kntro.reqsai.workspace.mothers.ProjectMother;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Application: Delete Project Role")
@ExtendWith(MockitoExtension.class)
class DeleteProjectRoleCommandHandlerTest {

    @Mock
    private ProjectRepository projects;
    @Mock
    private ProjectRoleRepository roles;
    @Mock
    private ProjectMemberRepository members;
    @InjectMocks
    private DeleteProjectRoleCommandHandler handler;

    @Test
    @DisplayName("should delete a role that no member is assigned to")
    void should_delete_unassigned_role() {
        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        DeleteProjectRoleCommand command = new DeleteProjectRoleCommand(orgId, projectId, roleId, UUID.randomUUID());
        ProjectRole role = new ProjectRole(projectId, "Analyst", Set.of(Permission.MEMBER_READ));

        when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ACTIVE))
                .thenReturn(Optional.of(ProjectMother.standard().withOrganizationId(orgId).build()));
        when(roles.findByIdAndProjectId(roleId, projectId)).thenReturn(Optional.of(role));
        when(members.countByProjectIdAndRoleId(projectId, roleId)).thenReturn(0L);

        handler.handle(command);

        verify(roles).delete(role);
    }

    @Test
    @DisplayName("should reject deleting a role that is still assigned to members")
    void should_reject_deleting_role_in_use() {
        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        DeleteProjectRoleCommand command = new DeleteProjectRoleCommand(orgId, projectId, roleId, UUID.randomUUID());
        ProjectRole role = new ProjectRole(projectId, "Analyst", Set.of(Permission.MEMBER_READ));

        when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ACTIVE))
                .thenReturn(Optional.of(ProjectMother.standard().withOrganizationId(orgId).build()));
        when(roles.findByIdAndProjectId(roleId, projectId)).thenReturn(Optional.of(role));
        when(members.countByProjectIdAndRoleId(projectId, roleId)).thenReturn(2L);

        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).error()).isEqualTo(WorkspaceError.PROJECT_ROLE_IN_USE));
        verify(roles, never()).delete(any());
    }

    @Test
    @DisplayName("should fail if the role does not exist")
    void should_fail_if_role_not_found() {
        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        DeleteProjectRoleCommand command = new DeleteProjectRoleCommand(orgId, projectId, roleId, UUID.randomUUID());

        when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ACTIVE))
                .thenReturn(Optional.of(ProjectMother.standard().withOrganizationId(orgId).build()));
        when(roles.findByIdAndProjectId(roleId, projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(command)).isInstanceOf(DomainException.class);
        verify(roles, never()).delete(any());
    }
}
