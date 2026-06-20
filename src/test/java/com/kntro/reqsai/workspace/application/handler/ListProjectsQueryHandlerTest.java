package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.mothers.ProjectMother;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("Application: List Projects")
@ExtendWith(MockitoExtension.class)
class ListProjectsQueryHandlerTest {

    @Mock
    private ProjectRepository projects;
    @InjectMocks
    private ListProjectsQueryHandler handler;

    @Test
    @DisplayName("should return the projects of the organization")
    void should_return_org_projects() {
        UUID orgId = UUID.randomUUID();
        List<Project> projectList = List.of(
                ProjectMother.standard().withOrganizationId(orgId).build(),
                ProjectMother.standard().withOrganizationId(orgId).build());
        when(projects.findAllByOrganizationId(orgId)).thenReturn(projectList);

        assertThat(handler.handle(orgId)).hasSize(2).isEqualTo(projectList);
    }
}
