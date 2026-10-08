package com.kntro.reqsai.workspace.domain.valueobjects;

import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import org.jspecify.annotations.Nullable;

/**
 * A client document (PDF or Word) uploaded by an analyst, validated before any byte of it is parsed:
 * a known extension, at most {@link #MAX_BYTES}, not empty, a compatible declared content type and the
 * magic bytes of its format (see {@link DocumentFormat}). The file name is reduced to its last path
 * segment, stripped of control characters and capped at {@link #FILE_NAME_MAX} characters.
 *
 * @param fileName the sanitized original file name (e.g. {@code Contrato marco.pdf})
 * @param format   the detected format
 * @param content  the raw bytes
 */
public record ClientDocumentFile(String fileName, DocumentFormat format, byte[] content) {

    /** Largest accepted document: 50 MB, the same as {@code spring.servlet.multipart.max-file-size}. */
    public static final long MAX_BYTES = 50L * 1024 * 1024;
    public static final int FILE_NAME_MAX = 255;

    /** Validates an upload; throws {@code DOCUMENT_TYPE_NOT_ALLOWED}, {@code DOCUMENT_TOO_LARGE} or {@code DOCUMENT_EMPTY}. */
    public static ClientDocumentFile of(@Nullable String originalFileName, @Nullable String declaredContentType,
                                        byte @Nullable [] content) {
        String fileName = sanitizeFileName(originalFileName);
        DocumentFormat format = DocumentFormat.fromFileName(fileName);
        checkSize(content == null ? 0 : content.length);
        if (content == null || content.length == 0) {
            throw WorkspaceExceptions.documentEmpty("The uploaded file is empty");
        }
        format.verifyDeclaredType(declaredContentType);
        format.verifyContent(content);
        return new ClientDocumentFile(fileName, format, content);
    }

    /** Throws {@code DOCUMENT_TOO_LARGE} when {@code sizeBytes} exceeds {@link #MAX_BYTES}. */
    public static void checkSize(long sizeBytes) {
        if (sizeBytes > MAX_BYTES) {
            throw WorkspaceExceptions.documentTooLarge(sizeBytes, MAX_BYTES);
        }
    }

    public long sizeBytes() {
        return content.length;
    }

    /** The last path segment of the name, without control characters, capped (extension kept); rejects a blank name. */
    static String sanitizeFileName(@Nullable String originalFileName) {
        String name = originalFileName == null ? "" : originalFileName;
        int separator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        name = name.substring(separator + 1)
                .replaceAll("\\p{Cntrl}", "")
                .strip();
        if (name.isEmpty()) {
            throw WorkspaceExceptions.documentTypeNotAllowed("the file has no name");
        }
        if (name.length() > FILE_NAME_MAX) {
            String extension = DocumentFormat.extensionOf(name);
            int keep = FILE_NAME_MAX - extension.length() - 1;
            name = extension.isEmpty() || keep <= 0
                    ? name.substring(0, FILE_NAME_MAX)
                    : name.substring(0, keep).strip() + "." + extension;
        }
        return name;
    }

    @Override
    public String toString() {
        return "ClientDocumentFile[" + fileName + ", " + format + ", " + content.length + " bytes]";
    }
}
