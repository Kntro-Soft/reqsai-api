package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.command.ArchiveProjectCommand;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import com.kntro.reqsai.workspace.mothers.ProjectMother;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

@DisplayName("Application: Archive Project")
@ExtendWith(MockitoExtension.class)
class ArchiveProjectCommandHandlerTest {

    @Mock
    private ProjectRepository projects;
    @InjectMocks
    private ArchiveProjectCommandHandler handler;

    @Nested
    @DisplayName("Successful archive")
    class SuccessfulArchive {

        @Test
        @DisplayName("should archive project successfully")
        void should_archive_project_successfully() {
            UUID orgId = UUID.randomUUID();
            Project project = ProjectMother.standard().withOrganizationId(orgId).build();
            UUID projectId = project.getId();
            ArchiveProjectCommand command = new ArchiveProjectCommand(orgId, projectId, UUID.randomUUID());

            when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ACTIVE))
                    .thenReturn(Optional.of(project));

            handler.handle(command);

            assertThat(project.getStatus()).isEqualTo(ProjectStatus.ARCHIVED);
            verify(projects).save(project);
        }
    }

    @Nested
    @DisplayName("Validation failures")
    class ValidationFailures {

        @Test
        @DisplayName("should fail if project does not exist")
        void should_fail_if_project_not_found() {
            UUID orgId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            ArchiveProjectCommand command = new ArchiveProjectCommand(orgId, projectId, UUID.randomUUID());

            when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(DomainException.class);
            verify(projects, never()).save(any());
        }
    }
}
