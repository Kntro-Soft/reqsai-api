package com.kntro.reqsai.workspace.domain.valueobjects;

import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * The client-document formats ReqsAI reads text from: PDF and Word (Office Open XML, {@code .docx}).
 * <p>
 * A file is accepted only when three independent signals agree: its <strong>extension</strong>, the
 * <strong>content type</strong> the client declared (a generic {@code application/octet-stream} is
 * tolerated, since browsers send it when the OS has no mapping), and its <strong>magic bytes</strong>.
 * Renaming an executable to {@code .pdf} therefore fails on the content check, and an executable
 * signature ({@code MZ}, ELF, Mach-O, shebang) is reported as such.
 */
public enum DocumentFormat {

    PDF("pdf", "application/pdf",
            Set.of("application/pdf", "application/x-pdf", "application/acrobat")),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/zip", "application/x-zip-compressed"));

    /** Declared content types that say nothing about the file and are therefore not held against it. */
    private static final Set<String> GENERIC_TYPES = Set.of(
            "", "application/octet-stream", "binary/octet-stream", "application/x-download",
            "application/force-download", "application/unknown");

    /** A PDF header may be preceded by a few bytes of garbage; readers scan the first kilobyte. */
    private static final int PDF_HEADER_WINDOW = 1024;
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP_MAGIC = {'P', 'K', 3, 4};

    private static final byte[][] EXECUTABLE_MAGICS = {
            {'M', 'Z'},                                          // Windows PE / DOS (.exe, .dll)
            {0x7F, 'E', 'L', 'F'},                               // Linux ELF
            {(byte) 0xFE, (byte) 0xED, (byte) 0xFA, (byte) 0xCE}, // Mach-O 32-bit
            {(byte) 0xFE, (byte) 0xED, (byte) 0xFA, (byte) 0xCF}, // Mach-O 64-bit
            {(byte) 0xCE, (byte) 0xFA, (byte) 0xED, (byte) 0xFE}, // Mach-O 32-bit, reversed
            {(byte) 0xCF, (byte) 0xFA, (byte) 0xED, (byte) 0xFE}, // Mach-O 64-bit, reversed
            {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE}, // Mach-O universal / Java class
            {'#', '!'}                                           // script with a shebang
    };

    private final String extension;
    private final String mediaType;
    private final Set<String> acceptedContentTypes;

    DocumentFormat(String extension, String mediaType, Set<String> acceptedContentTypes) {
        this.extension = extension;
        this.mediaType = mediaType;
        this.acceptedContentTypes = acceptedContentTypes;
    }

    public String extension() {
        return extension;
    }

    /** The canonical media type stored with the document. */
    public String mediaType() {
        return mediaType;
    }

    /** The format named by the file's extension, or {@code DOCUMENT_TYPE_NOT_ALLOWED}. */
    public static DocumentFormat fromFileName(String fileName) {
        String extension = extensionOf(fileName);
        return Arrays.stream(values())
                .filter(format -> format.extension.equals(extension))
                .findFirst()
                .orElseThrow(() -> WorkspaceExceptions.documentTypeNotAllowed(extension.isEmpty()
                        ? "the file has no extension"
                        : "'." + extension + "' files are not accepted"));
    }

    /** Rejects a declared content type that names another kind of file (e.g. {@code application/x-msdownload}). */
    public void verifyDeclaredType(@Nullable String contentType) {
        String normalized = normalizeContentType(contentType);
        if (!GENERIC_TYPES.contains(normalized) && !acceptedContentTypes.contains(normalized)) {
            throw WorkspaceExceptions.documentTypeNotAllowed(
                    "declared content type '" + normalized + "' is not a " + name() + " document");
        }
    }

    /** Rejects bytes that are an executable or do not start like this format. */
    public void verifyContent(byte[] content) {
        if (looksExecutable(content)) {
            throw WorkspaceExceptions.documentTypeNotAllowed("executable files are not accepted");
        }
        boolean matches = switch (this) {
            case PDF -> indexOf(content, PDF_MAGIC, PDF_HEADER_WINDOW) >= 0;
            case DOCX -> startsWith(content, ZIP_MAGIC);
        };
        if (!matches) {
            throw WorkspaceExceptions.documentTypeNotAllowed(
                    "the content of the file is not a " + name() + " document");
        }
    }

    /** Whether the bytes start with a known executable or script signature. */
    public static boolean looksExecutable(byte[] content) {
        return Arrays.stream(EXECUTABLE_MAGICS).anyMatch(magic -> startsWith(content, magic));
    }

    static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 || dot == fileName.length() - 1 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String normalizeContentType(@Nullable String contentType) {
        if (contentType == null) {
            return "";
        }
        int separator = contentType.indexOf(';');
        String base = separator >= 0 ? contentType.substring(0, separator) : contentType;
        return base.strip().toLowerCase(Locale.ROOT);
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static int indexOf(byte[] content, byte[] needle, int window) {
        int limit = Math.min(content.length, window) - needle.length;
        for (int i = 0; i <= limit; i++) {
            boolean found = true;
            for (int j = 0; j < needle.length; j++) {
                if (content[i + j] != needle[j]) {
                    found = false;
                    break;
                }
            }
            if (found) {
                return i;
            }
        }
        return -1;
    }
}
