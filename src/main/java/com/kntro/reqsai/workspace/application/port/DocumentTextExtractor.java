package com.kntro.reqsai.workspace.application.port;

import com.kntro.reqsai.workspace.domain.valueobjects.ClientDocumentFile;

/**
 * Reads the plain text of a validated client document (PDF or Word). Implementations guard against
 * hostile files (zip bombs, encrypted or malformed documents) and cap the text at
 * {@code ProjectDocumentContent.MAX_CHARS} characters.
 */
public interface DocumentTextExtractor {

    /**
     * Extracts the document's text, normalized (no control characters, collapsed blank lines).
     *
     * @throws com.kntro.reqsai.shared.domain.exception.DomainException {@code DOCUMENT_UNREADABLE} when the
     *         file cannot be parsed, {@code DOCUMENT_EMPTY} when it holds no extractable text (e.g. a scan),
     *         or {@code DOCUMENT_TYPE_NOT_ALLOWED} when its inner structure is not the declared format
     */
    ExtractedText extract(ClientDocumentFile file);
}
