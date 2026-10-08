package com.kntro.reqsai.workspace.domain.model;

import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.Assert;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.valueobjects.ClientDocumentFile;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * A document of a project: either a metadata-only record, or a client document (PDF/Word) uploaded by
 * an analyst. An upload carries the original file name, media type and size, the extracted text (in a
 * lazily loaded {@link ProjectDocumentContent}) and, once the analyst applies the AI review, a context
 * {@link #getSummary() summary} that the AI reads as project context.
 * <p>
 * Uploads start {@link DocumentStatus#PENDING} and become {@link DocumentStatus#ACTIVE} through
 * {@link #applyReview(String, DocumentType, String)}.
 */
@Getter
@Entity
@Table(name = "project_documents")
public class ProjectDocument extends AggregateRoot {

    public static final int NAME_MAX = 255;
    public static final int SUMMARY_MAX = 4000;

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

    @Column(name = "file_name", length = ClientDocumentFile.FILE_NAME_MAX, updatable = false)
    private @Nullable String fileName;

    @Column(name = "media_type", length = 100, updatable = false)
    private @Nullable String mediaType;

    @Column(name = "size_bytes", updatable = false)
    private @Nullable Long sizeBytes;

    @Column(name = "extracted_chars", updatable = false)
    private @Nullable Integer extractedChars;

    @Column(name = "summary", columnDefinition = "text")
    private @Nullable String summary;

    @Getter(AccessLevel.NONE)
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "content_id", updatable = false)
    private @Nullable ProjectDocumentContent content;

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

    /**
     * An uploaded client document awaiting the analyst's review, named after the file.
     *
     * @param text      the extracted text, at most {@link ProjectDocumentContent#MAX_CHARS} characters
     * @param truncated whether the extraction dropped text beyond that cap
     */
    public static ProjectDocument pendingUpload(UUID projectId, ClientDocumentFile file, DocumentType documentType,
                                                String text, boolean truncated) {
        Assert.notNull(file, "file");
        ProjectDocument document = new ProjectDocument(projectId, file.fileName(), documentType);
        document.status = DocumentStatus.PENDING;
        document.fileName = file.fileName();
        document.mediaType = file.format().mediaType();
        document.sizeBytes = file.sizeBytes();
        document.content = new ProjectDocumentContent(text, truncated);
        document.extractedChars = text.length();
        return document;
    }

    public static String normalizeName(String name) {
        return Assert.maxLength(Assert.notBlank(name, "name"), "name", NAME_MAX);
    }

    /** Trims the summary; a blank one is stored as {@code null}. */
    public static @Nullable String normalizeSummary(@Nullable String summary) {
        if (summary == null || summary.isBlank()) {
            return null;
        }
        return Assert.maxLength(summary.strip(), "summary", SUMMARY_MAX);
    }

    public void updateMetadata(String name, DocumentType documentType) {
        this.name = normalizeName(name);
        this.documentType = Assert.notNull(documentType, "documentType");
    }

    /**
     * Completes the review of an uploaded document: the analyst's name, type and context summary are
     * stored and the document becomes {@link DocumentStatus#ACTIVE}. Only a pending document can be applied.
     */
    public void applyReview(String name, DocumentType documentType, @Nullable String summary) {
        if (status != DocumentStatus.PENDING) {
            throw WorkspaceExceptions.projectDocumentNotPending(getId());
        }
        this.name = normalizeName(name);
        this.documentType = Assert.notNull(documentType, "documentType");
        this.summary = normalizeSummary(summary);
        this.status = DocumentStatus.ACTIVE;
    }

    public boolean isPending() {
        return status == DocumentStatus.PENDING;
    }

    /** The extracted text of an uploaded document (loads it on first access); empty for metadata-only records. */
    public Optional<String> extractedText() {
        return Optional.ofNullable(content).map(ProjectDocumentContent::getBody);
    }

    /** Whether the extraction dropped text beyond {@link ProjectDocumentContent#MAX_CHARS}. */
    public boolean isTextTruncated() {
        return content != null && content.isTruncated();
    }
}
