package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.command.UpdateProjectCommand;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Application: Update Project")
@ExtendWith(MockitoExtension.class)
class UpdateProjectCommandHandlerTest {

    @Mock
    private ProjectRepository projects;
    @Mock
    private OrganizationRepository organizations;
    @InjectMocks
    private UpdateProjectCommandHandler handler;

    @Nested
    @DisplayName("Successful update")
    class SuccessfulUpdate {

        @Test
        @DisplayName("should update project details successfully and save")
        void should_update_project_successfully() {
            // Arrange
            Organization org = OrganizationMother.active().build();
            UUID orgId = org.getId();
            Project project = ProjectMother.standard().withOrganizationId(orgId).build();
            UUID projectId = project.getId();

            UpdateProjectCommand command = new UpdateProjectCommand(
                    orgId,
                    projectId,
                    "Updated Project Name",
                    "Updated Description",
                    List.of("Kotlin"),
                    List.of("Micronaut"),
                    List.of("Mobile"),
                    List.of("MongoDB"),
                    "Microservices",
                    "Logistics",
                    UUID.randomUUID()
            );

            when(organizations.findById(orgId)).thenReturn(Optional.of(org));
            when(projects.findById(projectId)).thenReturn(Optional.of(project));
            when(projects.existsByNameAndIdNot(command.name(), projectId)).thenReturn(false);
            when(projects.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            Project updated = handler.handle(command);

            // Assert
            assertThat(updated).isNotNull();
            assertThat(updated.getName()).isEqualTo("Updated Project Name");
            assertThat(updated.getDescription()).isEqualTo("Updated Description");
            assertThat(updated.getTechnicalProfile().programmingLanguages()).containsExactly("Kotlin");
            verify(projects).save(project);
        }
    }

    @Nested
    @DisplayName("Validation failures")
    class ValidationFailures {

        @Test
        @DisplayName("should fail if organization does not exist")
        void should_fail_if_org_not_found() {
            // Arrange
            UUID orgId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            UpdateProjectCommand command = new UpdateProjectCommand(
                    orgId, projectId, "Name", "Desc", List.of("Java"), List.of("Spring"),
                    List.of("Web"), List.of("PostgreSQL"), "Clean", "Fintech", UUID.randomUUID());

            when(organizations.findById(orgId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(DomainException.class);
            verify(projects, never()).save(any());
        }

        @Test
        @DisplayName("should fail if project does not exist")
        void should_fail_if_project_not_found() {
            // Arrange
            Organization org = OrganizationMother.active().build();
            UUID orgId = org.getId();
            UUID projectId = UUID.randomUUID();
            UpdateProjectCommand command = new UpdateProjectCommand(
                    orgId, projectId, "Name", "Desc", List.of("Java"), List.of("Spring"),
                    List.of("Web"), List.of("PostgreSQL"), "Clean", "Fintech", UUID.randomUUID());

            when(organizations.findById(orgId)).thenReturn(Optional.of(org));
            when(projects.findById(projectId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(DomainException.class);
            verify(projects, never()).save(any());
        }

        @Test
        @DisplayName("should fail if project does not belong to organization")
        void should_fail_if_project_belongs_to_other_org() {
            // Arrange
            Organization org = OrganizationMother.active().build();
            UUID orgId = org.getId();
            Project project = ProjectMother.standard().withOrganizationId(UUID.randomUUID()).build(); // belongs to other org
            UUID projectId = project.getId();
            UpdateProjectCommand command = new UpdateProjectCommand(
                    orgId, projectId, "Name", "Desc", List.of("Java"), List.of("Spring"),
                    List.of("Web"), List.of("PostgreSQL"), "Clean", "Fintech", UUID.randomUUID());

            when(organizations.findById(orgId)).thenReturn(Optional.of(org));
            when(projects.findById(projectId)).thenReturn(Optional.of(project));

            // Act & Assert
            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(DomainException.class);
            verify(projects, never()).save(any());
        }

        @Test
        @DisplayName("should fail if name already exists in another project of the organization")
        void should_fail_if_name_exists() {
            // Arrange
            Organization org = OrganizationMother.active().build();
            UUID orgId = org.getId();
            Project project = ProjectMother.standard().withOrganizationId(orgId).build();
            UUID projectId = project.getId();
            UpdateProjectCommand command = new UpdateProjectCommand(
                    orgId, projectId, "Duplicate Name", "Desc", List.of("Java"), List.of("Spring"),
                    List.of("Web"), List.of("PostgreSQL"), "Clean", "Fintech", UUID.randomUUID());

            when(organizations.findById(orgId)).thenReturn(Optional.of(org));
            when(projects.findById(projectId)).thenReturn(Optional.of(project));
            when(projects.existsByNameAndIdNot("Duplicate Name", projectId)).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(DomainException.class);
            verify(projects, never()).save(any());
        }
    }
}
