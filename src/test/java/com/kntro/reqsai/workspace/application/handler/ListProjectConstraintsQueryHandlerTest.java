package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.query.ListProjectConstraintsQuery;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("Application: List Project Constraints")
@ExtendWith(MockitoExtension.class)
class ListProjectConstraintsQueryHandlerTest {

    @Mock
    private OrganizationRepository organizations;
    @Mock
    private ProjectRepository projects;
    @InjectMocks
    private ListProjectConstraintsQueryHandler handler;

    @Test
    @DisplayName("should list project constraints successfully")
    void should_list_project_constraints_successfully() {
        Organization organization = OrganizationMother.active().build();
        Project project = ProjectMother.standard().withOrganizationId(organization.getId()).build();
        project.addConstraint("Must integrate with SAP");
        project.addConstraint("Must use Azure AD");
        ListProjectConstraintsQuery query = new ListProjectConstraintsQuery(
                organization.getId(), project.getId(), UUID.randomUUID());

        when(organizations.findById(organization.getId())).thenReturn(Optional.of(organization));
        when(projects.findByIdAndOrganizationIdAndStatus(project.getId(), organization.getId(), ProjectStatus.ACTIVE))
                .thenReturn(Optional.of(project));

        List<ProjectConstraint> result = handler.handle(query);

        assertThat(result).hasSize(2);
    }
}
