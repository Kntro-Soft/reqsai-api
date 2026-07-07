package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.command.DeleteProjectConstraintCommand;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Application: Delete Project Constraint")
@ExtendWith(MockitoExtension.class)
class DeleteProjectConstraintCommandHandlerTest {

    @Mock
    private OrganizationRepository organizations;
    @Mock
    private ProjectRepository projects;
    @InjectMocks
    private DeleteProjectConstraintCommandHandler handler;

    @Test
    @DisplayName("should delete project constraint successfully")
    void should_delete_project_constraint_successfully() {
        Organization organization = OrganizationMother.active().build();
        Project project = ProjectMother.standard().withOrganizationId(organization.getId()).build();
        ProjectConstraint constraint = project.addConstraint("Must integrate with SAP");
        DeleteProjectConstraintCommand command = new DeleteProjectConstraintCommand(
                organization.getId(), project.getId(), constraint.getId(), UUID.randomUUID());

        when(organizations.findById(organization.getId())).thenReturn(Optional.of(organization));
        when(projects.findByIdAndOrganizationIdAndStatus(project.getId(), organization.getId(), ProjectStatus.ACTIVE))
                .thenReturn(Optional.of(project));
        when(projects.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        handler.handle(command);

        assertThat(project.getConstraints()).isEmpty();
        verify(projects).save(project);
    }

    @Test
    @DisplayName("should fail if project constraint does not exist")
    void should_fail_if_project_constraint_does_not_exist() {
        Organization organization = OrganizationMother.active().build();
        Project project = ProjectMother.standard().withOrganizationId(organization.getId()).build();
        DeleteProjectConstraintCommand command = new DeleteProjectConstraintCommand(
                organization.getId(), project.getId(), UUID.randomUUID(), UUID.randomUUID());

        when(organizations.findById(organization.getId())).thenReturn(Optional.of(organization));
        when(projects.findByIdAndOrganizationIdAndStatus(project.getId(), organization.getId(), ProjectStatus.ACTIVE))
                .thenReturn(Optional.of(project));

        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(DomainException.class);
    }
}
