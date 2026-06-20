package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.EntityNotFoundException;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.model.Project;
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
    void should_return_when_in_org() {
        UUID orgId = UUID.randomUUID();
        Project project = ProjectMother.standard().withOrganizationId(orgId).build();
        when(projects.findById(project.getId())).thenReturn(Optional.of(project));

        assertThat(handler.handle(orgId, project.getId())).isSameAs(project);
    }

    @Test
    @DisplayName("should fail when the project does not exist")
    void should_fail_when_missing() {
        UUID projectId = UUID.randomUUID();
        when(projects.findById(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(UUID.randomUUID(), projectId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("should fail when the project belongs to another organization")
    void should_fail_when_other_org() {
        Project project = ProjectMother.standard().withOrganizationId(UUID.randomUUID()).build();
        when(projects.findById(project.getId())).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> handler.handle(UUID.randomUUID(), project.getId()))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
