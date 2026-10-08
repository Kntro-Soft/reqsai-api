package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.command.RestoreDemoProjectCommand;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.service.DemoProjectSeeder;
import com.kntro.reqsai.workspace.application.service.DemoProjectTemplate;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceError;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Application: Restore demo project data")
@ExtendWith(MockitoExtension.class)
class RestoreDemoProjectCommandHandlerTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private ProjectRepository projects;
    @Mock
    private DemoProjectSeeder seeder;
    @InjectMocks
    private RestoreDemoProjectCommandHandler handler;

    @Test
    @DisplayName("restores an active demo project through the seeder")
    void restores_demo_project() {
        Project demo = DemoProjectTemplate.newProject(ORG_ID, USER_ID);
        when(projects.findByIdAndOrganizationIdAndStatus(demo.getId(), ORG_ID, ProjectStatus.ACTIVE))
                .thenReturn(Optional.of(demo));
        when(seeder.restore(demo, USER_ID)).thenReturn(demo);

        Project restored = handler.handle(new RestoreDemoProjectCommand(ORG_ID, demo.getId(), USER_ID));

        assertThat(restored).isSameAs(demo);
        verify(seeder).restore(demo, USER_ID);
    }

    @Test
    @DisplayName("rejects a regular project with PROJECT_NOT_DEMO")
    void rejects_regular_project() {
        Project regular = ProjectMother.standard().withOrganizationId(ORG_ID).build();
        when(projects.findByIdAndOrganizationIdAndStatus(regular.getId(), ORG_ID, ProjectStatus.ACTIVE))
                .thenReturn(Optional.of(regular));

        assertThatThrownBy(() -> handler.handle(new RestoreDemoProjectCommand(ORG_ID, regular.getId(), USER_ID)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).error())
                .isEqualTo(WorkspaceError.PROJECT_NOT_DEMO);
        verify(seeder, never()).restore(any(), any());
    }

    @Test
    @DisplayName("fails with PROJECT_NOT_FOUND when the project is missing or archived")
    void fails_when_project_missing() {
        UUID projectId = UUID.randomUUID();
        when(projects.findByIdAndOrganizationIdAndStatus(projectId, ORG_ID, ProjectStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new RestoreDemoProjectCommand(ORG_ID, projectId, USER_ID)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).error())
                .isEqualTo(WorkspaceError.PROJECT_NOT_FOUND);
        verify(seeder, never()).restore(any(), any());
    }
}
