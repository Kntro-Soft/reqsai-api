package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.command.DeleteProjectCommand;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.model.Project;
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
    @InjectMocks
    private DeleteProjectCommandHandler handler;

    @Nested
    @DisplayName("Successful deletion")
    class SuccessfulDeletion {

        @Test
        @DisplayName("should delete project successfully from repository")
        void should_delete_project_successfully() {
            // Arrange
            UUID orgId = UUID.randomUUID();
            Project project = ProjectMother.standard().withOrganizationId(orgId).build();
            UUID projectId = project.getId();
            DeleteProjectCommand command = new DeleteProjectCommand(orgId, projectId, UUID.randomUUID());

            when(projects.findById(projectId)).thenReturn(Optional.of(project));

            // Act
            handler.handle(command);

            // Assert
            verify(projects).delete(project);
        }
    }

    @Nested
    @DisplayName("Validation failures")
    class ValidationFailures {

        @Test
        @DisplayName("should fail if project does not exist")
        void should_fail_if_project_not_found() {
            // Arrange
            UUID orgId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            DeleteProjectCommand command = new DeleteProjectCommand(orgId, projectId, UUID.randomUUID());

            when(projects.findById(projectId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(DomainException.class);
            verify(projects, never()).delete(any());
        }

        @Test
        @DisplayName("should fail if project does not belong to organization")
        void should_fail_if_project_belongs_to_other_org() {
            // Arrange
            UUID orgId = UUID.randomUUID();
            Project project = ProjectMother.standard().withOrganizationId(UUID.randomUUID()).build(); // other org
            UUID projectId = project.getId();
            DeleteProjectCommand command = new DeleteProjectCommand(orgId, projectId, UUID.randomUUID());

            when(projects.findById(projectId)).thenReturn(Optional.of(project));

            // Act & Assert
            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(DomainException.class);
            verify(projects, never()).delete(any());
        }
    }
}
