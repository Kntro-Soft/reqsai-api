package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.shared.interfaces.pagination.PageCriteria;
import com.kntro.reqsai.shared.interfaces.pagination.PageRequestFactory;
import com.kntro.reqsai.shared.interfaces.pagination.PaginationProperties;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.query.ListProjectsQuery;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import com.kntro.reqsai.workspace.mothers.OrganizationMother;
import com.kntro.reqsai.workspace.mothers.ProjectMother;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@DisplayName("Application: List Projects")
@ExtendWith(MockitoExtension.class)
class ListProjectsQueryHandlerTest {

    @Mock
    private ProjectRepository projects;
    @Mock
    private OrganizationRepository organizations;

    private final PageRequestFactory pageRequestFactory = new PageRequestFactory(new PaginationProperties(20, 100));

    private ListProjectsQueryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ListProjectsQueryHandler(projects, organizations, pageRequestFactory);
    }

    @Test
    @DisplayName("should list projects for the organization")
    void should_list_projects_for_the_organization() {
        Organization org = OrganizationMother.active().build();
        UUID orgId = org.getId();
        Project project = ProjectMother.standard().withOrganizationId(orgId).build();
        Page<Project> projectPage = new PageImpl<>(List.of(project), PageRequest.of(0, 20), 1);

        when(organizations.findById(orgId)).thenReturn(Optional.of(org));
        when(projects.findAllByOrganizationIdAndStatus(eq(orgId), eq(ProjectStatus.ACTIVE), any())).thenReturn(projectPage);

        Page<Project> result = handler.handle(new ListProjectsQuery(orgId, PageCriteria.of(0, 20, "createdAt", "DESC")));

        assertThat(result.getContent()).containsExactly(project);
    }

    @Test
    @DisplayName("should fail when organization does not exist")
    void should_fail_when_organization_does_not_exist() {
        UUID orgId = UUID.randomUUID();

        when(organizations.findById(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new ListProjectsQuery(orgId, PageCriteria.of(0, 20, null, null))))
                .isInstanceOf(DomainException.class);
    }
}
