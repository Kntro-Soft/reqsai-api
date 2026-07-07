package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.query.GetProjectQuery;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
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
    @InjectMocks
    private GetProjectQueryHandler handler;

    @Test
    @DisplayName("should return the project when it belongs to the organization")
    void should_return_project_when_it_belongs_to_organization() {
        UUID orgId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        Project project = ProjectMother.standard().withOrganizationId(orgId).build();

        when(projects.findByIdAndOrganizationIdAndStatus(project.getId(), orgId, ProjectStatus.ACTIVE))
                .thenReturn(Optional.of(project));

        Project result = handler.handle(new GetProjectQuery(orgId, project.getId(), requestedBy));

        assertThat(result).isEqualTo(project);
    }

    @Test
    @DisplayName("should fail when project is not active within the organization")
    void should_fail_when_project_is_not_active_within_the_organization() {
        UUID orgId = UUID.randomUUID();
        Project project = ProjectMother.standard().withOrganizationId(UUID.randomUUID()).build();

        when(projects.findByIdAndOrganizationIdAndStatus(project.getId(), orgId, ProjectStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new GetProjectQuery(orgId, project.getId(), UUID.randomUUID())))
                .isInstanceOf(DomainException.class);
    }
}
