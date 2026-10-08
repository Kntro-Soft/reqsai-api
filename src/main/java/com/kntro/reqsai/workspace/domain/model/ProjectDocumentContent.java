package com.kntro.reqsai.workspace.domain.model;

import com.kntro.reqsai.shared.domain.model.AuditableEntity;
import com.kntro.reqsai.shared.domain.support.Assert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * The plain text extracted from an uploaded client document, kept apart from the {@link ProjectDocument}
 * row (and loaded lazily) so listing documents never reads up to {@link #MAX_CHARS} characters per row.
 * Non-root entity: created, loaded and deleted only through its {@link ProjectDocument}.
 */
@Entity
@Table(name = "project_document_contents")
@Getter
public class ProjectDocumentContent extends AuditableEntity {

    /** Extracted text is capped at this many characters; anything beyond is dropped and flagged as truncated. */
    public static final int MAX_CHARS = 200_000;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "truncated", nullable = false)
    private boolean truncated;

    protected ProjectDocumentContent() {
        super();
    }

    ProjectDocumentContent(String body, boolean truncated) {
        super();
        Assert.isTrue(body != null && !body.isBlank(), "body", "must contain text");
        this.body = Assert.maxLength(body, "body", MAX_CHARS);
        this.truncated = truncated;
    }
}
