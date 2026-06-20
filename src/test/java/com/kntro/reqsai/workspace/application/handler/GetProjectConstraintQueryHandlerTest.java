package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.query.GetProjectConstraintQuery;
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
import static org.mockito.Mockito.when;

@DisplayName("Application: Get Project Constraint")
@ExtendWith(MockitoExtension.class)
class GetProjectConstraintQueryHandlerTest {

    @Mock
    private OrganizationRepository organizations;
    @Mock
    private ProjectRepository projects;
    @InjectMocks
    private GetProjectConstraintQueryHandler handler;

    @Test
    @DisplayName("should get project constraint successfully")
    void should_get_project_constraint_successfully() {
        Organization organization = OrganizationMother.active().build();
        Project project = ProjectMother.standard().withOrganizationId(organization.getId()).build();
        ProjectConstraint constraint = project.addConstraint("Must integrate with SAP");
        GetProjectConstraintQuery query = new GetProjectConstraintQuery(
                organization.getId(), project.getId(), constraint.getId(), UUID.randomUUID());

        when(organizations.findById(organization.getId())).thenReturn(Optional.of(organization));
        when(projects.findByIdAndOrganizationIdAndStatus(project.getId(), organization.getId(), ProjectStatus.ACTIVE))
                .thenReturn(Optional.of(project));

        ProjectConstraint result = handler.handle(query);

        assertThat(result.getId()).isEqualTo(constraint.getId());
    }

    @Test
    @DisplayName("should fail if project constraint does not exist")
    void should_fail_if_project_constraint_does_not_exist() {
        Organization organization = OrganizationMother.active().build();
        Project project = ProjectMother.standard().withOrganizationId(organization.getId()).build();
        GetProjectConstraintQuery query = new GetProjectConstraintQuery(
                organization.getId(), project.getId(), UUID.randomUUID(), UUID.randomUUID());

        when(organizations.findById(organization.getId())).thenReturn(Optional.of(organization));
        when(projects.findByIdAndOrganizationIdAndStatus(project.getId(), organization.getId(), ProjectStatus.ACTIVE))
                .thenReturn(Optional.of(project));

        assertThatThrownBy(() -> handler.handle(query))
                .isInstanceOf(DomainException.class);
    }
}
