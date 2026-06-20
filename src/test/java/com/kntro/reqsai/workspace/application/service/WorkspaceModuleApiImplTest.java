package com.kntro.reqsai.workspace.application.service;

import com.kntro.reqsai.workspace.api.ProjectSnapshot;
import com.kntro.reqsai.workspace.application.port.GlossaryRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.model.Glossary;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.mothers.GlossaryBuilder;
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
import static org.mockito.Mockito.when;

@DisplayName("Application: WorkspaceModuleApi")
@ExtendWith(MockitoExtension.class)
class WorkspaceModuleApiImplTest {

    @Mock
    private ProjectRepository projects;

    @Mock
    private GlossaryRepository glossaries;

    @InjectMocks
    private WorkspaceModuleApiImpl api;

    @Nested
    @DisplayName("findProjectSnapshot")
    class FindProjectSnapshot {

        @Test
        @DisplayName("should return snapshot with constraints and glossary terms")
        void should_return_full_snapshot() {
            Project project = ProjectMother.standard()
                    .withName("Payment Platform")
                    .withDescription("Handles payments.")
                    .build();
            project.addConstraint("Must comply with PCI-DSS.");
            project.addConstraint("Max response time 200ms.");

            Glossary glossary = GlossaryBuilder.aGlossary().withProjectId(project.getId()).build();
            glossary.addTerm("Sprint", "Fixed-length iteration.");
            glossary.addTerm("Backlog", "Prioritized list of features.");

            when(projects.findById(project.getId())).thenReturn(Optional.of(project));
            when(glossaries.findByProjectId(project.getId())).thenReturn(Optional.of(glossary));

            Optional<ProjectSnapshot> result = api.findProjectSnapshot(project.getId());

            assertThat(result).isPresent();
            ProjectSnapshot snap = result.get();
            assertThat(snap.projectId()).isEqualTo(project.getId());
            assertThat(snap.name()).isEqualTo("Payment Platform");
            assertThat(snap.description()).isEqualTo("Handles payments.");
            assertThat(snap.constraints()).containsExactlyInAnyOrder(
                    "Must comply with PCI-DSS.", "Max response time 200ms.");
            assertThat(snap.glossaryTerms()).hasSize(2);
            assertThat(snap.glossaryTerms()).extracting("term")
                    .containsExactlyInAnyOrder("Sprint", "Backlog");
        }

        @Test
        @DisplayName("should return snapshot with empty glossary when none provisioned")
        void should_return_snapshot_with_empty_glossary() {
            Project project = ProjectMother.standard().build();
            when(projects.findById(project.getId())).thenReturn(Optional.of(project));
            when(glossaries.findByProjectId(project.getId())).thenReturn(Optional.empty());

            Optional<ProjectSnapshot> result = api.findProjectSnapshot(project.getId());

            assertThat(result).isPresent();
            assertThat(result.get().glossaryTerms()).isEmpty();
            assertThat(result.get().constraints()).isEmpty();
        }

        @Test
        @DisplayName("should return empty when project does not exist")
        void should_return_empty_when_project_not_found() {
            UUID unknownId = UUID.randomUUID();
            when(projects.findById(unknownId)).thenReturn(Optional.empty());

            Optional<ProjectSnapshot> result = api.findProjectSnapshot(unknownId);

            assertThat(result).isEmpty();
        }
    }
}
