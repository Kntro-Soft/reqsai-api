package com.kntro.reqsai.workspace.domain.valueobjects;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Domain: ClientDocumentFile (US22 upload validation)")
class ClientDocumentFileTest {

    private static final byte[] PDF = "%PDF-1.7\n%âãÏÓ\n1 0 obj".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] DOCX = {'P', 'K', 3, 4, 20, 0, 6, 0};
    private static final byte[] EXE = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0};

    @Nested
    @DisplayName("accepts")
    class Accepts {

        @Test
        @DisplayName("a PDF by extension, content type and magic bytes")
        void pdf() {
            ClientDocumentFile file = ClientDocumentFile.of("Contrato.PDF", "application/pdf", PDF);

            assertThat(file.format()).isEqualTo(DocumentFormat.PDF);
            assertThat(file.fileName()).isEqualTo("Contrato.PDF");
            assertThat(file.sizeBytes()).isEqualTo(PDF.length);
            assertThat(file.format().mediaType()).isEqualTo("application/pdf");
        }

        @Test
        @DisplayName("a Word .docx, also when the browser sends a generic content type")
        void docx_with_generic_type() {
            assertThat(ClientDocumentFile.of("acta.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", DOCX).format())
                    .isEqualTo(DocumentFormat.DOCX);
            assertThat(ClientDocumentFile.of("acta.docx", "application/octet-stream", DOCX).format())
                    .isEqualTo(DocumentFormat.DOCX);
            assertThat(ClientDocumentFile.of("acta.docx", null, DOCX).format()).isEqualTo(DocumentFormat.DOCX);
        }

        @Test
        @DisplayName("a PDF whose header follows a few bytes of garbage")
        void pdf_with_leading_garbage() {
            byte[] content = ("\n\n  " + new String(PDF, StandardCharsets.ISO_8859_1)).getBytes(StandardCharsets.ISO_8859_1);

            assertThat(ClientDocumentFile.of("a.pdf", "application/pdf", content).format()).isEqualTo(DocumentFormat.PDF);
        }

        @Test
        @DisplayName("keeps only the last path segment of the file name and caps it, extension included")
        void sanitizes_file_name() {
            assertThat(ClientDocumentFile.of("C:\\fakepath\\TdR.pdf", "application/pdf", PDF).fileName())
                    .isEqualTo("TdR.pdf");
            assertThat(ClientDocumentFile.of("../../etc/brief.pdf", "application/pdf", PDF).fileName())
                    .isEqualTo("brief.pdf");

            String longName = "a".repeat(400) + ".pdf";
            String capped = ClientDocumentFile.of(longName, "application/pdf", PDF).fileName();
            assertThat(capped).hasSize(ClientDocumentFile.FILE_NAME_MAX).endsWith(".pdf");
        }
    }

    @Nested
    @DisplayName("rejects with DOCUMENT_TYPE_NOT_ALLOWED (415)")
    class RejectsType {

        @ParameterizedTest
        @ValueSource(strings = {"setup.exe", "notas.doc", "datos.xlsx", "script.sh", "imagen.png", "sin-extension", "punto."})
        @DisplayName("a file whose extension is not .pdf or .docx")
        void other_extensions(String name) {
            assertError(() -> ClientDocumentFile.of(name, "application/octet-stream", PDF),
                    WorkspaceError.DOCUMENT_TYPE_NOT_ALLOWED);
        }

        @Test
        @DisplayName("an executable renamed to .pdf, naming it as an executable")
        void executable_renamed_to_pdf() {
            assertThatThrownBy(() -> ClientDocumentFile.of("contrato.pdf", "application/pdf", EXE))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("executable")
                    .extracting(e -> ((DomainException) e).error())
                    .isEqualTo(WorkspaceError.DOCUMENT_TYPE_NOT_ALLOWED);
        }

        @Test
        @DisplayName("a .docx whose bytes are not a ZIP package")
        void docx_without_zip_magic() {
            assertError(() -> ClientDocumentFile.of("acta.docx", null, PDF), WorkspaceError.DOCUMENT_TYPE_NOT_ALLOWED);
        }

        @Test
        @DisplayName("a declared content type of another kind of file")
        void declared_executable_type() {
            assertError(() -> ClientDocumentFile.of("contrato.pdf", "application/x-msdownload", PDF),
                    WorkspaceError.DOCUMENT_TYPE_NOT_ALLOWED);
            assertError(() -> ClientDocumentFile.of("contrato.pdf", "image/png", PDF),
                    WorkspaceError.DOCUMENT_TYPE_NOT_ALLOWED);
        }

        @Test
        @DisplayName("ELF, Mach-O and shebang signatures as executables")
        void other_executables() {
            assertThat(DocumentFormat.looksExecutable(new byte[]{0x7F, 'E', 'L', 'F', 2})).isTrue();
            assertThat(DocumentFormat.looksExecutable(new byte[]{(byte) 0xCF, (byte) 0xFA, (byte) 0xED, (byte) 0xFE})).isTrue();
            assertThat(DocumentFormat.looksExecutable("#!/bin/sh".getBytes(StandardCharsets.US_ASCII))).isTrue();
            assertThat(DocumentFormat.looksExecutable(PDF)).isFalse();
            assertThat(DocumentFormat.looksExecutable(DOCX)).isFalse();
        }
    }

    @Nested
    @DisplayName("rejects by size and emptiness")
    class RejectsSize {

        @Test
        @DisplayName("a file over 50 MB with DOCUMENT_TOO_LARGE (413)")
        void too_large() {
            ClientDocumentFile.checkSize(ClientDocumentFile.MAX_BYTES);

            assertThatThrownBy(() -> ClientDocumentFile.checkSize(ClientDocumentFile.MAX_BYTES + 1))
                    .isInstanceOf(DomainException.class)
                    .extracting(e -> ((DomainException) e).error())
                    .satisfies(error -> {
                        assertThat(error).isEqualTo(WorkspaceError.DOCUMENT_TOO_LARGE);
                        assertThat(error.status()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
                    });
            assertThat(ClientDocumentFile.MAX_BYTES).isEqualTo(50L * 1024 * 1024);
        }

        @Test
        @DisplayName("an empty file with DOCUMENT_EMPTY (422)")
        void empty() {
            assertError(() -> ClientDocumentFile.of("vacio.pdf", "application/pdf", new byte[0]), WorkspaceError.DOCUMENT_EMPTY);
            assertError(() -> ClientDocumentFile.of("vacio.pdf", "application/pdf", null), WorkspaceError.DOCUMENT_EMPTY);
        }

        @Test
        @DisplayName("a blank file name")
        void blank_name() {
            assertError(() -> ClientDocumentFile.of("  ", "application/pdf", PDF), WorkspaceError.DOCUMENT_TYPE_NOT_ALLOWED);
            assertError(() -> ClientDocumentFile.of(null, "application/pdf", PDF), WorkspaceError.DOCUMENT_TYPE_NOT_ALLOWED);
        }
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, WorkspaceError expected) {
        assertThatThrownBy(call)
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).error())
                .isEqualTo(expected);
    }
}
