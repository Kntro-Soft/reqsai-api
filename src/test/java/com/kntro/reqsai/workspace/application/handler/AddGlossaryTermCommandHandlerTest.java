package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.EntityNotFoundException;
import com.kntro.reqsai.workspace.application.command.AddGlossaryTermCommand;
import com.kntro.reqsai.workspace.application.port.GlossaryRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.model.Glossary;
import com.kntro.reqsai.workspace.domain.model.GlossaryTerm;
import com.kntro.reqsai.workspace.mothers.GlossaryBuilder;
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

@DisplayName("Application: Add Glossary Term")
@ExtendWith(MockitoExtension.class)
class AddGlossaryTermCommandHandlerTest {

    @Mock
    private GlossaryRepository glossaries;

    @Mock
    private ProjectRepository projects;

    @InjectMocks
    private AddGlossaryTermCommandHandler handler;

    @Nested
    @DisplayName("Successful creation")
    class Success {

        @Test
        @DisplayName("should add the term and persist the glossary")
        void should_add_term_and_persist() {
            UUID orgId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            com.kntro.reqsai.workspace.domain.model.Project project = org.mockito.Mockito.mock(com.kntro.reqsai.workspace.domain.model.Project.class);
            when(project.getOrganizationId()).thenReturn(orgId);
            when(projects.findById(projectId)).thenReturn(Optional.of(project));

            Glossary glossary = GlossaryBuilder.aGlossary().withProjectId(projectId).build();
            when(glossaries.findByProjectId(projectId)).thenReturn(Optional.of(glossary));
            when(glossaries.save(any())).thenAnswer(i -> i.getArgument(0));

            var command = new AddGlossaryTermCommand(orgId, projectId, "Sprint", "Fixed-length iteration in Scrum.", UUID.randomUUID());
            GlossaryTerm result = handler.handle(command);

            assertThat(result.getTerm()).isEqualTo("Sprint");
            assertThat(result.getDefinition()).isEqualTo("Fixed-length iteration in Scrum.");
            assertThat(result.getEmbedding()).isNull();
            verify(glossaries).save(glossary);
        }
    }

    @Nested
    @DisplayName("Failure cases")
    class Failures {

        @Test
        @DisplayName("should throw when no glossary exists for the project")
        void should_throw_when_glossary_not_found() {
            UUID orgId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            com.kntro.reqsai.workspace.domain.model.Project project = org.mockito.Mockito.mock(com.kntro.reqsai.workspace.domain.model.Project.class);
            when(project.getOrganizationId()).thenReturn(orgId);
            when(projects.findById(projectId)).thenReturn(Optional.of(project));
            when(glossaries.findByProjectId(projectId)).thenReturn(Optional.empty());

            var command = new AddGlossaryTermCommand(orgId, projectId, "Sprint", "A time-box.", UUID.randomUUID());

            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(EntityNotFoundException.class);
            verify(glossaries, never()).save(any());
        }

        @Test
        @DisplayName("should throw when project organization ID mismatch")
        void should_throw_when_org_mismatch() {
            UUID commandOrgId = UUID.randomUUID();
            UUID projectOrgId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            com.kntro.reqsai.workspace.domain.model.Project project = org.mockito.Mockito.mock(com.kntro.reqsai.workspace.domain.model.Project.class);
            when(project.getOrganizationId()).thenReturn(projectOrgId);
            when(projects.findById(projectId)).thenReturn(Optional.of(project));

            var command = new AddGlossaryTermCommand(commandOrgId, projectId, "Sprint", "A time-box.", UUID.randomUUID());

            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(EntityNotFoundException.class);
            verify(glossaries, never()).save(any());
        }

        @Test
        @DisplayName("should throw when project not found")
        void should_throw_when_project_not_found() {
            UUID orgId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            when(projects.findById(projectId)).thenReturn(Optional.empty());

            var command = new AddGlossaryTermCommand(orgId, projectId, "Sprint", "A time-box.", UUID.randomUUID());

            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(EntityNotFoundException.class);
            verify(glossaries, never()).save(any());
        }
    }
}
