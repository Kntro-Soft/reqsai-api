package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.command.CreateProjectCommand;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import com.kntro.reqsai.workspace.domain.valueobjects.PlanLimits;
import com.kntro.reqsai.workspace.mothers.CreateProjectCommandMother;
import com.kntro.reqsai.workspace.mothers.OrganizationMother;
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
import static org.mockito.Mockito.*;

@DisplayName("Application: Create Project")
@ExtendWith(MockitoExtension.class)
class CreateProjectCommandHandlerTest {

    @Mock
    private ProjectRepository projects;
    @Mock
    private OrganizationRepository organizations;
    @InjectMocks
    private CreateProjectCommandHandler handler;

    @Nested
    @DisplayName("Successful creation")
    class SuccessfulCreation {

        @Test
        @DisplayName("should create a project successfully and persist it")
        void should_create_project_successfully() {
            // Arrange
            Organization org = OrganizationMother.active().withPlanLimits(PlanLimits.free()).build();
            UUID orgId = org.getId();
            CreateProjectCommand command = CreateProjectCommandMother.withOrganizationId(orgId);

            when(organizations.findById(orgId)).thenReturn(Optional.of(org));
            when(projects.existsByOrganizationIdAndNameAndStatus(orgId, command.name(), ProjectStatus.ACTIVE)).thenReturn(false);
            when(projects.countActiveByOrganizationId(orgId)).thenReturn(0);
            when(projects.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            Project project = handler.handle(command);

            // Assert
            assertThat(project).isNotNull();
            assertThat(project.getOrganizationId()).isEqualTo(orgId);
            assertThat(project.getName()).isEqualTo(command.name());
            assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
            verify(projects).save(any(Project.class));
        }
    }

    @Nested
    @DisplayName("Validation failures")
    class ValidationFailures {

        @Test
        @DisplayName("should fail if organization does not exist")
        void should_fail_if_organization_does_not_exist() {
            // Arrange
            UUID orgId = UUID.randomUUID();
            CreateProjectCommand command = CreateProjectCommandMother.withOrganizationId(orgId);
            when(organizations.findById(orgId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(DomainException.class);
            verify(projects, never()).save(any());
        }

        @Test
        @DisplayName("should fail if project name already exists in organization")
        void should_fail_if_project_name_exists() {
            // Arrange
            Organization org = OrganizationMother.active().build();
            UUID orgId = org.getId();
            CreateProjectCommand command = CreateProjectCommandMother.withOrganizationId(orgId);

            when(organizations.findById(orgId)).thenReturn(Optional.of(org));
            when(projects.existsByOrganizationIdAndNameAndStatus(orgId, command.name(), ProjectStatus.ACTIVE)).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(DomainException.class);
            verify(projects, never()).save(any());
        }

        @Test
        @DisplayName("should allow the same project name in another organization")
        void should_allow_same_name_in_other_organization() {
            // Arrange
            Organization org = OrganizationMother.active().build();
            UUID orgId = org.getId();
            CreateProjectCommand command = CreateProjectCommandMother.withOrganizationId(orgId);

            when(organizations.findById(orgId)).thenReturn(Optional.of(org));
            when(projects.existsByOrganizationIdAndNameAndStatus(orgId, command.name(), ProjectStatus.ACTIVE)).thenReturn(false);
            when(projects.countActiveByOrganizationId(orgId)).thenReturn(0);
            when(projects.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            Project project = handler.handle(command);

            // Assert
            assertThat(project.getName()).isEqualTo(command.name());
            verify(projects).existsByOrganizationIdAndNameAndStatus(orgId, command.name(), ProjectStatus.ACTIVE);
            verify(projects).save(any(Project.class));
        }

        @Test
        @DisplayName("should fail if project plan limit is exceeded")
        void should_fail_if_plan_limit_exceeded() {
            // Arrange
            // plan limits maxProjects = 1
            Organization org = OrganizationMother.active().withPlanLimits(PlanLimits.free()).build();
            UUID orgId = org.getId();
            CreateProjectCommand command = CreateProjectCommandMother.withOrganizationId(orgId);

            when(organizations.findById(orgId)).thenReturn(Optional.of(org));
            when(projects.existsByOrganizationIdAndNameAndStatus(orgId, command.name(), ProjectStatus.ACTIVE)).thenReturn(false);
            // already 1 active project
            when(projects.countActiveByOrganizationId(orgId)).thenReturn(1);

            // Act & Assert
            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(DomainException.class);
            verify(projects, never()).save(any());
        }

        @Test
        @DisplayName("should ignore active projects from other organizations when enforcing plan limit")
        void should_ignore_other_organizations_when_enforcing_plan_limit() {
            // Arrange
            Organization org = OrganizationMother.active().build();
            UUID orgId = org.getId();
            CreateProjectCommand command = CreateProjectCommandMother.withOrganizationId(orgId);

            when(organizations.findById(orgId)).thenReturn(Optional.of(org));
            when(projects.existsByOrganizationIdAndNameAndStatus(orgId, command.name(), ProjectStatus.ACTIVE)).thenReturn(false);
            when(projects.countActiveByOrganizationId(orgId)).thenReturn(0);
            when(projects.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            Project project = handler.handle(command);

            // Assert
            assertThat(project).isNotNull();
            verify(projects).countActiveByOrganizationId(orgId);
            verify(projects).save(any(Project.class));
        }
    }
}
