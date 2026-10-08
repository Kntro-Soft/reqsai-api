package com.kntro.reqsai.workspace.application.service;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.shared.domain.exception.EntityNotFoundException;
import com.kntro.reqsai.workspace.application.port.GlossaryRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectDocumentRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.service.PendingDocumentService.ProjectKnowledge;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceError;
import com.kntro.reqsai.workspace.domain.model.DocumentStatus;
import com.kntro.reqsai.workspace.domain.model.DocumentType;
import com.kntro.reqsai.workspace.domain.model.Glossary;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.model.ProjectDocument;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import com.kntro.reqsai.workspace.domain.valueobjects.ClientDocumentFile;
import com.kntro.reqsai.workspace.domain.valueobjects.PlanLimits;
import com.kntro.reqsai.workspace.mothers.OrganizationMother;
import com.kntro.reqsai.workspace.mothers.ProjectMother;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Application: PendingDocumentService")
@ExtendWith(MockitoExtension.class)
class PendingDocumentServiceTest {

    @Mock private OrganizationRepository organizations;
    @Mock private ProjectRepository projects;
    @Mock private ProjectDocumentRepository documents;
    @Mock private GlossaryRepository glossaries;
    @InjectMocks private PendingDocumentService service;

    @Test
    @DisplayName("preflight reads the existing glossary terms and constraints as duplicate keys")
    void preflight_reads_project_knowledge() {
        Organization organization = OrganizationMother.active().build();
        Project project = ProjectMother.standard().withOrganizationId(organization.getId())
                .withName("Restaurante").withDomain("Gastronomía").build();
        project.addConstraint("Debe cumplir la Ley 29733.");
        Glossary glossary = new Glossary(project.getId());
        glossary.addTerm("Comensal", "Cliente", UUID.randomUUID());
        when(organizations.findById(organization.getId())).thenReturn(Optional.of(organization));
        when(projects.findByIdAndOrganizationIdAndStatus(project.getId(), organization.getId(), ProjectStatus.ACTIVE))
                .thenReturn(Optional.of(project));
        when(documents.countByProjectIdAndStatus(project.getId(), DocumentStatus.ACTIVE)).thenReturn(2);
        when(glossaries.findByProjectId(project.getId())).thenReturn(Optional.of(glossary));

        ProjectKnowledge knowledge = service.preflight(organization.getId(), project.getId());

        assertThat(knowledge.projectName()).isEqualTo("Restaurante");
        assertThat(knowledge.projectDomain()).isEqualTo("Gastronomía");
        assertThat(knowledge.hasTerm(" COMENSAL ")).isTrue();
        assertThat(knowledge.hasTerm("Reserva")).isFalse();
        assertThat(knowledge.hasConstraint("debe cumplir la  ley 29733.")).isTrue();
    }

    @Test
    @DisplayName("preflight fails fast when the project's document quota is used up or the project is missing")
    void preflight_enforces_quota_and_project() {
        Organization organization = OrganizationMother.active()
                .withPlanLimits(new PlanLimits(3, 25, 2, 100_000L, 50)).build();
        Project project = ProjectMother.standard().withOrganizationId(organization.getId()).build();
        when(organizations.findById(organization.getId())).thenReturn(Optional.of(organization));
        when(projects.findByIdAndOrganizationIdAndStatus(project.getId(), organization.getId(), ProjectStatus.ACTIVE))
                .thenReturn(Optional.of(project));
        when(documents.countByProjectIdAndStatus(project.getId(), DocumentStatus.ACTIVE)).thenReturn(2);

        assertThatThrownBy(() -> service.preflight(organization.getId(), project.getId()))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).error())
                .isEqualTo(WorkspaceError.PROJECT_DOCUMENT_PLAN_LIMIT_EXCEEDED);

        UUID missing = UUID.randomUUID();
        when(projects.findByIdAndOrganizationIdAndStatus(missing, organization.getId(), ProjectStatus.ACTIVE))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.preflight(organization.getId(), missing))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("savePending replaces an unapplied analysis of the same file and drops stale ones")
    @SuppressWarnings("unchecked")
    void save_pending_replaces_superseded() {
        UUID projectId = UUID.randomUUID();
        ProjectDocument sameName = pending(projectId, "TdR.pdf", Instant.now());
        ProjectDocument stale = pending(projectId, "Antiguo.pdf", Instant.now().minus(Duration.ofHours(30)));
        ProjectDocument recent = pending(projectId, "Otro.pdf", Instant.now().minus(Duration.ofHours(1)));
        ProjectDocument fresh = pending(projectId, "tdr.PDF", null);
        when(documents.findAllByProjectIdAndStatus(projectId, DocumentStatus.PENDING))
                .thenReturn(List.of(sameName, stale, recent));
        when(documents.save(any(ProjectDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectDocument saved = service.savePending(fresh);

        ArgumentCaptor<Collection<ProjectDocument>> removed = ArgumentCaptor.forClass(Collection.class);
        verify(documents).deleteAllAndFlush(removed.capture());
        assertThat(removed.getValue()).containsExactlyInAnyOrder(sameName, stale);
        assertThat(saved).isSameAs(fresh);
    }

    private static ProjectDocument pending(UUID projectId, String name, Instant createdAt) {
        ClientDocumentFile file = ClientDocumentFile.of(name, null, "%PDF-1.4".getBytes(StandardCharsets.US_ASCII));
        ProjectDocument document = ProjectDocument.pendingUpload(projectId, file, DocumentType.REFERENCE, "texto", false);
        if (createdAt != null) {
            ReflectionTestUtils.setField(document, "createdAt", createdAt);
        }
        return document;
    }
}
