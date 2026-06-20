package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.command.UpdateProjectConstraintCommand;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.model.ProjectConstraint;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import com.kntro.reqsai.workspace.mothers.OrganizationMother;
import com.kntro.reqsai.workspace.mothers.ProjectMother;
import org.junit.jupiter.api.DisplayName;
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

@DisplayName("Application: Update Project Constraint")
@ExtendWith(MockitoExtension.class)
class UpdateProjectConstraintCommandHandlerTest {

    @Mock
    private OrganizationRepository organizations;
    @Mock
    private ProjectRepository projects;
    @InjectMocks
    private UpdateProjectConstraintCommandHandler handler;

    @Test
    @DisplayName("should update project constraint successfully")
    void should_update_project_constraint_successfully() {
        Organization organization = OrganizationMother.active().build();
        Project project = ProjectMother.standard().withOrganizationId(organization.getId()).build();
        ProjectConstraint constraint = project.addConstraint("Must integrate with SAP");
        UpdateProjectConstraintCommand command = new UpdateProjectConstraintCommand(
                organization.getId(), project.getId(), constraint.getId(), "Must integrate with SAP ECC", UUID.randomUUID());

        when(organizations.findById(organization.getId())).thenReturn(Optional.of(organization));
        when(projects.findByIdAndOrganizationIdAndStatus(project.getId(), organization.getId(), ProjectStatus.ACTIVE))
                .thenReturn(Optional.of(project));
        when(projects.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectConstraint updated = handler.handle(command);

        assertThat(updated.getDescription()).isEqualTo("Must integrate with SAP ECC");
        verify(projects).save(project);
    }

    @Test
    @DisplayName("should fail if update causes duplicate constraint")
    void should_fail_if_update_causes_duplicate_constraint() {
        Organization organization = OrganizationMother.active().build();
        Project project = ProjectMother.standard().withOrganizationId(organization.getId()).build();
        project.addConstraint("Must integrate with SAP");
        ProjectConstraint second = project.addConstraint("Must use Azure AD");
        UpdateProjectConstraintCommand command = new UpdateProjectConstraintCommand(
                organization.getId(), project.getId(), second.getId(), " must integrate with sap ", UUID.randomUUID());

        when(organizations.findById(organization.getId())).thenReturn(Optional.of(organization));
        when(projects.findByIdAndOrganizationIdAndStatus(project.getId(), organization.getId(), ProjectStatus.ACTIVE))
                .thenReturn(Optional.of(project));

        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(DomainException.class);
        verify(projects, never()).save(any());
    }
}
