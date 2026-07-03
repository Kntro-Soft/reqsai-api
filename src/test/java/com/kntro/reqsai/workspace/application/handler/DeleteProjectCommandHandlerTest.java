package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.command.DeleteProjectCommand;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.service.OrganizationAdminAccessService;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.Project;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Application: Delete Project")
@ExtendWith(MockitoExtension.class)
class DeleteProjectCommandHandlerTest {

    @Mock
    private ProjectRepository projects;
    @Mock
    private OrganizationRepository organizations;
    @Mock
    private OrganizationAdminAccessService orgAccess;
    @InjectMocks
    private DeleteProjectCommandHandler handler;

    @Nested
    @DisplayName("Successful deletion")
    class SuccessfulDeletion {

        @Test
        @DisplayName("should delete project physically")
        void should_delete_project_physically() {
            Organization org = OrganizationMother.active().build();
            UUID orgId = org.getId();
            Project project = ProjectMother.standard().withOrganizationId(orgId).build();
            UUID projectId = project.getId();
            DeleteProjectCommand command = new DeleteProjectCommand(orgId, projectId, UUID.randomUUID());

            when(organizations.findById(orgId)).thenReturn(Optional.of(org));
            when(projects.findByIdAndOrganizationId(projectId, orgId)).thenReturn(Optional.of(project));
            handler.handle(command);

            verify(projects).delete(project);
        }
    }

    @Nested
    @DisplayName("Validation failures")
    class ValidationFailures {

        @Test
        @DisplayName("should reject a caller who is not owner or admin")
        void should_reject_non_admin() {
            Organization org = OrganizationMother.active().build();
            UUID orgId = org.getId();
            DeleteProjectCommand command = new DeleteProjectCommand(orgId, UUID.randomUUID(), UUID.randomUUID());

            when(organizations.findById(orgId)).thenReturn(Optional.of(org));
            doThrow(WorkspaceExceptions.insufficientPermissions("delete project", command.requestedBy()))
                    .when(orgAccess).assertOwnerOrAdmin(any(), any(), any());

            assertThatThrownBy(() -> handler.handle(command)).isInstanceOf(DomainException.class);
            verify(projects, never()).delete(any());
        }

        @Test
        @DisplayName("should fail if project does not exist")
        void should_fail_if_project_not_found() {
            Organization org = OrganizationMother.active().build();
            UUID orgId = org.getId();
            UUID projectId = UUID.randomUUID();
            DeleteProjectCommand command = new DeleteProjectCommand(orgId, projectId, UUID.randomUUID());

            when(organizations.findById(orgId)).thenReturn(Optional.of(org));
            when(projects.findByIdAndOrganizationId(projectId, orgId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(DomainException.class);
            verify(projects, never()).delete(any());
        }
    }
}
