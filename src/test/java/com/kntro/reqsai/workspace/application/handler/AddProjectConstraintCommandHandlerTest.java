package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.EntityNotFoundException;
import com.kntro.reqsai.workspace.application.command.AddProjectConstraintCommand;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.model.ProjectConstraint;
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

@DisplayName("Application: Add Project Constraint")
@ExtendWith(MockitoExtension.class)
class AddProjectConstraintCommandHandlerTest {

    @Mock
    private ProjectRepository projects;

    @InjectMocks
    private AddProjectConstraintCommandHandler handler;

    @Nested
    @DisplayName("Successful creation")
    class Success {

        @Test
        @DisplayName("should add the constraint and persist the project")
        void should_add_constraint_and_persist() {
            Project project = ProjectMother.standard().build();
            when(projects.findById(project.getId())).thenReturn(Optional.of(project));
            when(projects.save(any())).thenAnswer(i -> i.getArgument(0));

            var command = new AddProjectConstraintCommand(project.getId(), "Must comply with PCI-DSS.", UUID.randomUUID());
            ProjectConstraint result = handler.handle(command);

            assertThat(result.getDescription()).isEqualTo("Must comply with PCI-DSS.");
            assertThat(result.getEmbedding()).isNull();
            verify(projects).save(project);
        }
    }

    @Nested
    @DisplayName("Failure cases")
    class Failures {

        @Test
        @DisplayName("should throw when project does not exist")
        void should_throw_when_project_not_found() {
            UUID projectId = UUID.randomUUID();
            when(projects.findById(projectId)).thenReturn(Optional.empty());

            var command = new AddProjectConstraintCommand(projectId, "PCI-DSS.", UUID.randomUUID());

            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(EntityNotFoundException.class);
            verify(projects, never()).save(any());
        }
    }
}
