package com.kntro.reqsai.workspace.domain.model;

import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.Assert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

@Getter
@Entity
@Table(name = "project_documents")
public class ProjectDocument extends AggregateRoot {

    public static final int NAME_MAX = 255;

    @Column(name = "project_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "name", nullable = false, length = NAME_MAX)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 32)
    private DocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private DocumentStatus status;

    protected ProjectDocument() {
        super();
    }

    public ProjectDocument(UUID projectId, String name, DocumentType documentType) {
        super();
        this.projectId = Assert.notNull(projectId, "projectId");
        this.name = normalizeName(name);
        this.documentType = Assert.notNull(documentType, "documentType");
        this.status = DocumentStatus.ACTIVE;
    }

    public static String normalizeName(String name) {
        return Assert.maxLength(Assert.notBlank(name, "name"), "name", NAME_MAX);
    }

    public void updateMetadata(String name, DocumentType documentType) {
        this.name = normalizeName(name);
        this.documentType = Assert.notNull(documentType, "documentType");
    }
}
