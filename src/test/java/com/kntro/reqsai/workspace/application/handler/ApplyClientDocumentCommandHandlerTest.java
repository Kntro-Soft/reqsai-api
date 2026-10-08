package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.shared.domain.exception.EntityNotFoundException;
import com.kntro.reqsai.workspace.application.command.ApplyClientDocumentCommand;
import com.kntro.reqsai.workspace.application.command.ApplyClientDocumentCommand.GlossaryTermInput;
import com.kntro.reqsai.workspace.application.port.GlossaryRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectDocumentRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.result.ClientDocumentApplyResult;
import com.kntro.reqsai.workspace.application.service.ProjectPermissionService;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceError;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.DocumentStatus;
import com.kntro.reqsai.workspace.domain.model.DocumentType;
import com.kntro.reqsai.workspace.domain.model.Glossary;
import com.kntro.reqsai.workspace.domain.model.GlossaryTerm;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.Permission;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.model.ProjectConstraint;
import com.kntro.reqsai.workspace.domain.model.ProjectDocument;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import com.kntro.reqsai.workspace.domain.valueobjects.ClientDocumentFile;
import com.kntro.reqsai.workspace.domain.valueobjects.PlanLimits;
import com.kntro.reqsai.workspace.mothers.OrganizationMother;
import com.kntro.reqsai.workspace.mothers.ProjectMother;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Application: Apply client document (US22)")
@ExtendWith(MockitoExtension.class)
class ApplyClientDocumentCommandHandlerTest {

    private static final UUID USER = UUID.randomUUID();

    @Mock private OrganizationRepository organizations;
    @Mock private ProjectRepository projects;
    @Mock private ProjectDocumentRepository documents;
    @Mock private GlossaryRepository glossaries;
    @Mock private ProjectPermissionService permissions;

    private ApplyClientDocumentCommandHandler handler;
    private Organization organization;
    private Project project;
    private Glossary glossary;
    private ProjectDocument document;

    @BeforeEach
    void setUp() {
        handler = new ApplyClientDocumentCommandHandler(organizations, projects, documents, glossaries, permissions);
        organization = OrganizationMother.active().withPlanLimits(new PlanLimits(3, 25, 10, 100_000L, 3)).build();
        project = ProjectMother.standard().withOrganizationId(organization.getId()).build();
        project.addConstraint("Debe cumplir la Ley 29733.");
        glossary = new Glossary(project.getId());
        glossary.addTerm("Comensal", "Cliente del restaurante", USER);
        document = ProjectDocument.pendingUpload(project.getId(),
                ClientDocumentFile.of("TdR.pdf", null, "%PDF-1.4".getBytes(StandardCharsets.US_ASCII)),
                DocumentType.REFERENCE, "Texto del documento", false);

        lenient().when(organizations.findById(organization.getId())).thenReturn(Optional.of(organization));
        lenient().when(projects.findByIdAndOrganizationIdAndStatus(project.getId(), organization.getId(), ProjectStatus.ACTIVE))
                .thenReturn(Optional.of(project));
        lenient().when(documents.findByIdAndProjectIdAndStatusIn(eq(document.getId()), eq(project.getId()), any()))
                .thenReturn(Optional.of(document));
        lenient().when(glossaries.findByProjectId(project.getId())).thenReturn(Optional.of(glossary));
        lenient().when(documents.save(any(ProjectDocument.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("adds the new terms and constraints, skips the existing ones and activates the document")
    void applies_review() {
        ClientDocumentApplyResult result = handler.handle(command("Términos de referencia", "Contexto del cliente.",
                List.of(new GlossaryTermInput("comensal", "Duplicado"), new GlossaryTermInput("Reserva", "Mesa apartada"),
                        new GlossaryTermInput(" RESERVA ", "Repetida en la selección")),
                List.of("debe cumplir la ley 29733.", "Disponible 24/7.")));

        assertThat(result.glossaryTermsAdded()).isEqualTo(1);
        assertThat(result.glossaryTermsSkipped()).isEqualTo(1);
        assertThat(result.constraintsAdded()).isEqualTo(1);
        assertThat(result.constraintsSkipped()).isEqualTo(1);
        assertThat(glossary.getTerms()).extracting(GlossaryTerm::getTerm).containsExactly("Comensal", "Reserva");
        assertThat(project.getConstraints()).extracting(ProjectConstraint::getDescription)
                .containsExactly("Debe cumplir la Ley 29733.", "Disponible 24/7.");
        assertThat(result.document().getStatus()).isEqualTo(DocumentStatus.ACTIVE);
        assertThat(result.document().getName()).isEqualTo("Términos de referencia");
        assertThat(result.document().getSummary()).isEqualTo("Contexto del cliente.");
        assertThat(result.document().getDocumentType()).isEqualTo(DocumentType.TECHNICAL_SPEC);
        verify(glossaries).save(glossary);
        verify(projects).save(project);
        verify(permissions).assertHasProjectPermission(eq(organization), eq(project.getId()), eq(USER),
                eq(Permission.GLOSSARY_TERM_WRITE), anyString());
        verify(permissions).assertHasProjectPermission(eq(organization), eq(project.getId()), eq(USER),
                eq(Permission.CONSTRAINT_WRITE), anyString());
    }

    @Test
    @DisplayName("keeps the file name when no name is given and checks no extra permission without selections")
    void applies_document_only() {
        ClientDocumentApplyResult result = handler.handle(command(null, null, List.of(), List.of()));

        assertThat(result.document().getName()).isEqualTo("TdR.pdf");
        assertThat(result.document().getSummary()).isNull();
        assertThat(result.glossaryTermsAdded() + result.constraintsAdded()).isZero();
        verify(permissions, never()).assertHasProjectPermission(any(), any(), any(), any(), anyString());
        verify(glossaries, never()).save(any());
        verify(projects, never()).save(any());
    }

    @Test
    @DisplayName("refuses glossary terms when the caller cannot write the glossary")
    void requires_glossary_permission() {
        doThrow(WorkspaceExceptions.insufficientPermissions("add glossary terms", USER))
                .when(permissions).assertHasProjectPermission(eq(organization), eq(project.getId()), eq(USER),
                        eq(Permission.GLOSSARY_TERM_WRITE), anyString());

        assertThatThrownBy(() -> handler.handle(command(null, null,
                List.of(new GlossaryTermInput("Reserva", "Mesa")), List.of())))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).error())
                .isEqualTo(WorkspaceError.INSUFFICIENT_PERMISSIONS);
        assertThat(document.isPending()).isTrue();
    }

    @Test
    @DisplayName("enforces the glossary plan limit for the terms it would add")
    void enforces_glossary_limit() {
        assertThatThrownBy(() -> handler.handle(command(null, null, List.of(
                new GlossaryTermInput("Reserva", "Mesa"), new GlossaryTermInput("Mesa", "Lugar"),
                new GlossaryTermInput("Turno", "Horario")), List.of())))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).error())
                .isEqualTo(WorkspaceError.GLOSSARY_TERM_PLAN_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("rejects a name another active document already uses, and an already applied document")
    void rejects_conflicts() {
        when(documents.existsByProjectIdAndNameAndStatus(project.getId(), "Duplicado", DocumentStatus.ACTIVE))
                .thenReturn(true);
        assertThatThrownBy(() -> handler.handle(command("Duplicado", null, List.of(), List.of())))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).error())
                .isEqualTo(WorkspaceError.PROJECT_DOCUMENT_ALREADY_EXISTS);

        handler.handle(command(null, null, List.of(), List.of()));
        assertThatThrownBy(() -> handler.handle(command(null, null, List.of(), List.of())))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).error())
                .isEqualTo(WorkspaceError.PROJECT_DOCUMENT_NOT_PENDING);
    }

    @Test
    @DisplayName("an unknown document is not found")
    void unknown_document() {
        UUID unknown = UUID.randomUUID();
        when(documents.findByIdAndProjectIdAndStatusIn(eq(unknown), eq(project.getId()),
                eq(Set.of(DocumentStatus.PENDING, DocumentStatus.ACTIVE)))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new ApplyClientDocumentCommand(organization.getId(), project.getId(),
                unknown, null, DocumentType.REFERENCE, null, List.of(), List.of(), USER)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    private ApplyClientDocumentCommand command(String name, String summary, List<GlossaryTermInput> terms,
                                               List<String> constraints) {
        return new ApplyClientDocumentCommand(organization.getId(), project.getId(), document.getId(), name,
                DocumentType.TECHNICAL_SPEC, summary, terms, constraints, USER);
    }
}
