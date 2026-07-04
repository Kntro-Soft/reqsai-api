package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.command.CreateProjectRoleCommand;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRoleRepository;
import com.kntro.reqsai.workspace.domain.model.Permission;
import com.kntro.reqsai.workspace.domain.model.Project;
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

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Application: Create Project Role")
@ExtendWith(MockitoExtension.class)
class CreateProjectRoleCommandHandlerTest {

    @Mock
    private ProjectRepository projects;
    @Mock
    private ProjectRoleRepository roles;
    @InjectMocks
    private CreateProjectRoleCommandHandler handler;

    @Nested
    @DisplayName("Successful creation")
    class SuccessfulCreation {

        @Test
        @DisplayName("should create project role in active project")
        void should_create_project_role_in_active_project() {
            UUID orgId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            UUID requestedBy = UUID.randomUUID();
            Project project = ProjectMother.standard().withOrganizationId(orgId).build();
            CreateProjectRoleCommand command = new CreateProjectRoleCommand(
                    orgId, projectId, "Analyst", Set.of(Permission.MEMBER_READ, Permission.DOCUMENT_READ), requestedBy);

            when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ACTIVE))
                    .thenReturn(Optional.of(project));
            when(roles.existsByProjectIdAndName(projectId, "Analyst")).thenReturn(false);
            when(roles.save(any(ProjectRole.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ProjectRole role = handler.handle(command);

            assertThat(role.getProjectId()).isEqualTo(projectId);
            assertThat(role.getPermissions()).contains(Permission.DOCUMENT_READ);
        }
    }

    @Nested
    @DisplayName("Validation failures")
    class ValidationFailures {

        @Test
        @DisplayName("should fail if project does not exist")
        void should_fail_if_project_does_not_exist() {
            UUID orgId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            UUID requestedBy = UUID.randomUUID();
            CreateProjectRoleCommand command = new CreateProjectRoleCommand(
                    orgId, projectId, "Analyst", Set.of(Permission.MEMBER_READ), requestedBy);

            when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> handler.handle(command)).isInstanceOf(DomainException.class);
            verify(roles, never()).save(any());
        }

        @Test
        @DisplayName("should fail if role name already exists")
        void should_fail_if_role_name_already_exists() {
            UUID orgId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            UUID requestedBy = UUID.randomUUID();
            CreateProjectRoleCommand command = new CreateProjectRoleCommand(
                    orgId, projectId, "Analyst", Set.of(Permission.MEMBER_READ), requestedBy);

            when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ACTIVE))
                    .thenReturn(Optional.of(ProjectMother.standard().withOrganizationId(orgId).build()));
            when(roles.existsByProjectIdAndName(projectId, "Analyst")).thenReturn(true);

            assertThatThrownBy(() -> handler.handle(command)).isInstanceOf(DomainException.class);
            verify(roles, never()).save(any());
        }
    }
}
