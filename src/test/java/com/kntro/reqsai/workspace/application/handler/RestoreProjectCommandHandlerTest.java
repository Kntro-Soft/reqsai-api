package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.command.RestoreProjectCommand;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.service.ProjectPermissionService;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import com.kntro.reqsai.workspace.mothers.OrganizationMother;
import com.kntro.reqsai.workspace.mothers.ProjectMother;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Application: Restore Project")
@ExtendWith(MockitoExtension.class)
class RestoreProjectCommandHandlerTest {

    @Mock
    private ProjectRepository projects;
    @Mock
    private OrganizationRepository organizations;
    @Mock
    private ProjectPermissionService projectPermission;
    @InjectMocks
    private RestoreProjectCommandHandler handler;

    @Nested
    @DisplayName("Successful restore")
    class SuccessfulRestore {

        @Test
        @DisplayName("should restore archived project successfully")
        void should_restore_archived_project_successfully() {
            Organization org = OrganizationMother.active().build();
            UUID orgId = org.getId();
            Project project = ProjectMother.standard().withOrganizationId(orgId).build();
            project.archive();
            UUID projectId = project.getId();
            RestoreProjectCommand command = new RestoreProjectCommand(orgId, projectId, UUID.randomUUID());

            when(organizations.findById(orgId)).thenReturn(Optional.of(org));
            when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ARCHIVED))
                    .thenReturn(Optional.of(project));
            when(projects.existsByOrganizationIdAndNameAndIdNotAndStatus(
                    orgId, project.getName(), projectId, ProjectStatus.ACTIVE)).thenReturn(false);

            handler.handle(command);

            assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
            verify(projects).save(project);
        }
    }

    @Nested
    @DisplayName("Validation failures")
    class ValidationFailures {

        @Test
        @DisplayName("should reject a caller without WRITE_PROJECT permission")
        void should_reject_without_write_permission() {
            Organization org = OrganizationMother.active().build();
            UUID orgId = org.getId();
            RestoreProjectCommand command = new RestoreProjectCommand(orgId, UUID.randomUUID(), UUID.randomUUID());

            when(organizations.findById(orgId)).thenReturn(Optional.of(org));
            doThrow(WorkspaceExceptions.insufficientPermissions("restore project", command.requestedBy()))
                    .when(projectPermission).assertHasProjectPermission(any(), any(), any(), any(), any());

            assertThatThrownBy(() -> handler.handle(command)).isInstanceOf(DomainException.class);
            verify(projects, never()).save(any());
        }

        @Test
        @DisplayName("should fail if archived project does not exist")
        void should_fail_if_archived_project_not_found() {
            Organization org = OrganizationMother.active().build();
            UUID orgId = org.getId();
            UUID projectId = UUID.randomUUID();
            RestoreProjectCommand command = new RestoreProjectCommand(orgId, projectId, UUID.randomUUID());

            when(organizations.findById(orgId)).thenReturn(Optional.of(org));
            when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ARCHIVED))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(DomainException.class);
            verify(projects, never()).save(any());
        }

        @Test
        @DisplayName("should fail if restore would collide with an active project name")
        void should_fail_if_restore_name_collides_with_active_project() {
            Organization org = OrganizationMother.active().build();
            UUID orgId = org.getId();
            Project project = ProjectMother.standard().withOrganizationId(orgId).build();
            project.archive();
            UUID projectId = project.getId();
            RestoreProjectCommand command = new RestoreProjectCommand(orgId, projectId, UUID.randomUUID());

            when(organizations.findById(orgId)).thenReturn(Optional.of(org));
            when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ARCHIVED))
                    .thenReturn(Optional.of(project));
            when(projects.existsByOrganizationIdAndNameAndIdNotAndStatus(
                    orgId, project.getName(), projectId, ProjectStatus.ACTIVE)).thenReturn(true);

            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(DomainException.class);
            verify(projects, never()).save(any());
        }
    }
}
