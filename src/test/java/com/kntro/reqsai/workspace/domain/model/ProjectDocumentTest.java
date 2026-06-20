package com.kntro.reqsai.workspace.domain.model;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
