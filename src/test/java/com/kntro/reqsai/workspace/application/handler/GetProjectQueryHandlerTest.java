package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.query.GetProjectQuery;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.Project;
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

@DisplayName("Application: Get Project")
@ExtendWith(MockitoExtension.class)
class GetProjectQueryHandlerTest {

    @Mock
    private ProjectRepository projects;
    @Mock
    private OrganizationRepository organizations;
    @InjectMocks
    private GetProjectQueryHandler handler;

    @Test
    @DisplayName("should return the project when it belongs to the organization")
    void should_return_project_when_it_belongs_to_organization() {
        Organization org = OrganizationMother.active().build();
        UUID orgId = org.getId();
        Project project = ProjectMother.standard().withOrganizationId(orgId).build();

        when(organizations.findById(orgId)).thenReturn(Optional.of(org));
        when(projects.findByIdAndOrganizationIdAndStatus(project.getId(), orgId, ProjectStatus.ACTIVE))
                .thenReturn(Optional.of(project));

        Project result = handler.handle(new GetProjectQuery(orgId, project.getId()));

        assertThat(result).isEqualTo(project);
    }

    @Test
    @DisplayName("should fail when organization does not exist")
    void should_fail_when_organization_does_not_exist() {
        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        when(organizations.findById(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new GetProjectQuery(orgId, projectId)))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("should fail when project is not active within the organization")
    void should_fail_when_project_is_not_active_within_the_organization() {
        Organization org = OrganizationMother.active().build();
        UUID orgId = org.getId();
        Project project = ProjectMother.standard().withOrganizationId(UUID.randomUUID()).build();

        when(organizations.findById(orgId)).thenReturn(Optional.of(org));
        when(projects.findByIdAndOrganizationIdAndStatus(project.getId(), orgId, ProjectStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new GetProjectQuery(orgId, project.getId())))
                .isInstanceOf(DomainException.class);
    }
}
