package com.kntro.reqsai.workspace.domain.model;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceError;
import com.kntro.reqsai.workspace.domain.valueobjects.ClientDocumentFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Domain: ProjectDocument Aggregate")
class ProjectDocumentTest {

    @Test
    @DisplayName("should create project document in ACTIVE status")
    void should_create_project_document_in_active_status() {
        UUID projectId = UUID.randomUUID();

        ProjectDocument document = new ProjectDocument(projectId, "Business Rules v1", DocumentType.BUSINESS_RULES);

        assertThat(document.getId()).isNotNull();
        assertThat(document.getProjectId()).isEqualTo(projectId);
        assertThat(document.getName()).isEqualTo("Business Rules v1");
        assertThat(document.getDocumentType()).isEqualTo(DocumentType.BUSINESS_RULES);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.ACTIVE);
    }

    @Test
    @DisplayName("should reject blank name")
    void should_reject_blank_name() {
        assertThatThrownBy(() -> new ProjectDocument(UUID.randomUUID(), "   ", DocumentType.BUSINESS_RULES))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("should reject null document type")
    void should_reject_null_document_type() {
        assertThatThrownBy(() -> new ProjectDocument(UUID.randomUUID(), "Business Rules v1", null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("should update metadata successfully")
    void should_update_metadata_successfully() {
        ProjectDocument document = new ProjectDocument(UUID.randomUUID(), "Business Rules v1", DocumentType.BUSINESS_RULES);

        document.updateMetadata("Technical Spec v1", DocumentType.TECHNICAL_SPEC);

        assertThat(document.getName()).isEqualTo("Technical Spec v1");
        assertThat(document.getDocumentType()).isEqualTo(DocumentType.TECHNICAL_SPEC);
    }

    @Test
    @DisplayName("an uploaded client document starts PENDING with its file metadata and extracted text")
    void pending_upload_keeps_file_metadata() {
        UUID projectId = UUID.randomUUID();
        ClientDocumentFile file = ClientDocumentFile.of("TdR Reservas.pdf", "application/pdf",
                "%PDF-1.7 content".getBytes(StandardCharsets.US_ASCII));

        ProjectDocument document = ProjectDocument.pendingUpload(projectId, file, DocumentType.TECHNICAL_SPEC,
                "Texto extraído del documento", false);

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(document.isPending()).isTrue();
        assertThat(document.getName()).isEqualTo("TdR Reservas.pdf");
        assertThat(document.getFileName()).isEqualTo("TdR Reservas.pdf");
        assertThat(document.getMediaType()).isEqualTo("application/pdf");
        assertThat(document.getSizeBytes()).isEqualTo(file.sizeBytes());
        assertThat(document.getExtractedChars()).isEqualTo("Texto extraído del documento".length());
        assertThat(document.extractedText()).contains("Texto extraído del documento");
        assertThat(document.isTextTruncated()).isFalse();
        assertThat(document.getSummary()).isNull();
    }

    @Test
    @DisplayName("applying the review activates the document with the reviewed name, type and summary")
    void apply_review_activates() {
        ProjectDocument document = pending();

        document.applyReview("  Términos de referencia  ", DocumentType.BUSINESS_RULES, "  Contexto del cliente.  ");

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.ACTIVE);
        assertThat(document.getName()).isEqualTo("Términos de referencia");
        assertThat(document.getDocumentType()).isEqualTo(DocumentType.BUSINESS_RULES);
        assertThat(document.getSummary()).isEqualTo("Contexto del cliente.");
    }

    @Test
    @DisplayName("a blank summary is stored as none and an oversized one is rejected")
    void summary_normalization() {
        assertThat(ProjectDocument.normalizeSummary("   ")).isNull();
        assertThat(ProjectDocument.normalizeSummary(null)).isNull();
        assertThatThrownBy(() -> ProjectDocument.normalizeSummary("x".repeat(ProjectDocument.SUMMARY_MAX + 1)))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("a document that is not pending cannot be applied again")
    void apply_twice_is_rejected() {
        ProjectDocument document = pending();
        document.applyReview("Doc", DocumentType.REFERENCE, null);

        assertThatThrownBy(() -> document.applyReview("Doc", DocumentType.REFERENCE, null))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).error())
                .isEqualTo(WorkspaceError.PROJECT_DOCUMENT_NOT_PENDING);
        assertThatThrownBy(() -> new ProjectDocument(UUID.randomUUID(), "Meta", DocumentType.REFERENCE)
                .applyReview("Meta", DocumentType.REFERENCE, null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("the extracted text must not be blank")
    void blank_text_is_rejected() {
        ClientDocumentFile file = ClientDocumentFile.of("a.pdf", null, "%PDF-1.4".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> ProjectDocument.pendingUpload(UUID.randomUUID(), file, DocumentType.REFERENCE, "  ", false))
                .isInstanceOf(DomainException.class);
    }

    private static ProjectDocument pending() {
        ClientDocumentFile file = ClientDocumentFile.of("acta.docx", null, new byte[]{'P', 'K', 3, 4, 0});
        return ProjectDocument.pendingUpload(UUID.randomUUID(), file, DocumentType.MEETING_NOTES, "Acta", true);
    }
}
