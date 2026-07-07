package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.command.AddProjectConstraintCommand;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.model.ProjectConstraint;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Application: Add Project Constraint")
@ExtendWith(MockitoExtension.class)
class AddProjectConstraintCommandHandlerTest {

    @Mock
    private OrganizationRepository organizations;
    @Mock
    private ProjectRepository projects;
    @InjectMocks
    private AddProjectConstraintCommandHandler handler;

    @Nested
    @DisplayName("Successful creation")
    class SuccessfulCreation {

        @Test
        @DisplayName("should create a project constraint successfully and persist the aggregate")
        void should_create_project_constraint_successfully() {
            Organization organization = OrganizationMother.active().build();
            Project project = ProjectMother.standard().withOrganizationId(organization.getId()).build();
            AddProjectConstraintCommand command = new AddProjectConstraintCommand(
                    organization.getId(), project.getId(), "Must integrate with SAP", UUID.randomUUID());

            when(organizations.findById(organization.getId())).thenReturn(Optional.of(organization));
            when(projects.findByIdAndOrganizationIdAndStatus(project.getId(), organization.getId(), ProjectStatus.ACTIVE))
                    .thenReturn(Optional.of(project));
            when(projects.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ProjectConstraint constraint = handler.handle(command);

            assertThat(constraint.getDescription()).isEqualTo("Must integrate with SAP");
            assertThat(project.getConstraints()).hasSize(1);
            verify(projects).save(project);
        }
    }

    @Nested
    @DisplayName("Validation failures")
    class ValidationFailures {

        @Test
        @DisplayName("should fail if organization does not exist")
        void should_fail_if_organization_does_not_exist() {
            UUID organizationId = UUID.randomUUID();
            AddProjectConstraintCommand command = new AddProjectConstraintCommand(
                    organizationId, UUID.randomUUID(), "Must integrate with SAP", UUID.randomUUID());

            when(organizations.findById(organizationId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(DomainException.class);
            verify(projects, never()).save(any());
        }

        @Test
        @DisplayName("should fail if project does not exist")
        void should_fail_if_project_does_not_exist() {
            Organization organization = OrganizationMother.active().build();
            AddProjectConstraintCommand command = new AddProjectConstraintCommand(
                    organization.getId(), UUID.randomUUID(), "Must integrate with SAP", UUID.randomUUID());

            when(organizations.findById(organization.getId())).thenReturn(Optional.of(organization));
            when(projects.findByIdAndOrganizationIdAndStatus(command.projectId(), organization.getId(), ProjectStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(DomainException.class);
            verify(projects, never()).save(any());
        }

        @Test
        @DisplayName("should fail if project is archived")
        void should_fail_if_project_is_archived() {
            Organization organization = OrganizationMother.active().build();
            Project project = ProjectMother.standard().withOrganizationId(organization.getId()).build();
            project.archive();
            AddProjectConstraintCommand command = new AddProjectConstraintCommand(
                    organization.getId(), project.getId(), "Must integrate with SAP", UUID.randomUUID());

            when(organizations.findById(organization.getId())).thenReturn(Optional.of(organization));
            when(projects.findByIdAndOrganizationIdAndStatus(project.getId(), organization.getId(), ProjectStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(DomainException.class);
            verify(projects, never()).save(any());
        }

        @Test
        @DisplayName("should fail if project constraint already exists")
        void should_fail_if_project_constraint_already_exists() {
            Organization organization = OrganizationMother.active().build();
            Project project = ProjectMother.standard().withOrganizationId(organization.getId()).build();
            project.addConstraint("Must integrate with SAP");
            AddProjectConstraintCommand command = new AddProjectConstraintCommand(
                    organization.getId(), project.getId(), "  must integrate with sap  ", UUID.randomUUID());

            when(organizations.findById(organization.getId())).thenReturn(Optional.of(organization));
            when(projects.findByIdAndOrganizationIdAndStatus(project.getId(), organization.getId(), ProjectStatus.ACTIVE))
                    .thenReturn(Optional.of(project));

            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(DomainException.class);
            verify(projects, never()).save(any());
        }
    }
}
