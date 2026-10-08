package com.kntro.reqsai.workspace.infrastructure.documents;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.testsupport.TestDocuments;
import com.kntro.reqsai.workspace.application.port.ExtractedText;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceError;
import com.kntro.reqsai.workspace.domain.model.ProjectDocumentContent;
import com.kntro.reqsai.workspace.domain.valueobjects.ClientDocumentFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Infra: PDF / Word text extraction (PDFBox + POI)")
class PdfWordTextExtractorTest {

    private final PdfWordTextExtractor extractor = new PdfWordTextExtractor();

    @Nested
    @DisplayName("reads")
    class Reads {

        @Test
        @DisplayName("the text of a PDF, Spanish accents included")
        void pdf() {
            byte[] pdf = TestDocuments.pdf("Términos de referencia: reservas en línea.",
                    "El comensal elige fecha, hora y número de personas.");

            ExtractedText text = extractor.extract(ClientDocumentFile.of("tdr.pdf", "application/pdf", pdf));

            assertThat(text.text())
                    .contains("Términos de referencia: reservas en línea.")
                    .contains("El comensal elige fecha, hora y número de personas.");
            assertThat(text.truncated()).isFalse();
        }

        @Test
        @DisplayName("the text of a Word .docx")
        void docx() {
            byte[] docx = TestDocuments.docx("Acta de reunión con el cliente.", "La aplicación debe cumplir la Ley 29733.");

            ExtractedText text = extractor.extract(ClientDocumentFile.of("acta.docx", null, docx));

            assertThat(text.text()).isEqualTo("Acta de reunión con el cliente.\nLa aplicación debe cumplir la Ley 29733.");
        }

        @Test
        @DisplayName("caps the text at 200,000 characters and flags it as truncated")
        void truncates_long_text() {
            String paragraph = "Requisito del cliente número uno con bastante detalle. ".repeat(40);
            String[] paragraphs = new String[110];
            java.util.Arrays.fill(paragraphs, paragraph);
            byte[] docx = TestDocuments.docx(paragraphs);

            ExtractedText text = extractor.extract(ClientDocumentFile.of("largo.docx", null, docx));

            assertThat(text.truncated()).isTrue();
            assertThat(text.text()).hasSizeLessThanOrEqualTo(ProjectDocumentContent.MAX_CHARS);
        }
    }

    @Nested
    @DisplayName("rejects")
    class Rejects {

        @Test
        @DisplayName("a PDF without text (a scan) with DOCUMENT_EMPTY")
        void pdf_without_text() {
            assertError(TestDocuments.blankPdf(), "scan.pdf", WorkspaceError.DOCUMENT_EMPTY);
        }

        @Test
        @DisplayName("a damaged PDF with DOCUMENT_UNREADABLE")
        void damaged_pdf() {
            assertError("%PDF-1.7\nthis is not really a pdf".getBytes(StandardCharsets.US_ASCII), "roto.pdf",
                    WorkspaceError.DOCUMENT_UNREADABLE);
        }

        @Test
        @DisplayName("a password-protected PDF with DOCUMENT_UNREADABLE")
        void protected_pdf() throws IOException {
            byte[] encrypted;
            try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                document.addPage(new PDPage());
                StandardProtectionPolicy policy = new StandardProtectionPolicy("owner", "secreto", new AccessPermission());
                policy.setEncryptionKeyLength(128);
                document.protect(policy);
                document.save(out);
                encrypted = out.toByteArray();
            }

            assertThatThrownBy(() -> extractor.extract(ClientDocumentFile.of("privado.pdf", null, encrypted)))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("password")
                    .extracting(e -> ((DomainException) e).error())
                    .isEqualTo(WorkspaceError.DOCUMENT_UNREADABLE);
        }

        @Test
        @DisplayName("a zip bomb disguised as .docx before POI loads it")
        void zip_bomb() throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
                zip.write(contentTypes("application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"));
                zip.closeEntry();
                zip.putNextEntry(new ZipEntry("word/document.xml"));
                byte[] zeros = new byte[1024 * 1024];
                for (int i = 0; i <= PdfWordTextExtractor.MAX_ZIP_INFLATED_BYTES / zeros.length; i++) {
                    zip.write(zeros);
                }
                zip.closeEntry();
            }
            byte[] bomb = out.toByteArray();
            assertThat(bomb.length).isLessThan(1024 * 1024);

            assertError(bomb, "bomba.docx", WorkspaceError.DOCUMENT_UNREADABLE);
        }

        @Test
        @DisplayName("an Excel or macro-enabled package renamed to .docx with DOCUMENT_TYPE_NOT_ALLOWED")
        void not_wordprocessing() throws IOException {
            assertError(zipWithContentTypes("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"),
                    "hoja.docx", WorkspaceError.DOCUMENT_TYPE_NOT_ALLOWED);
            assertError(zipWithContentTypes("application/vnd.ms-word.document.macroEnabled.main+xml"),
                    "macro.docx", WorkspaceError.DOCUMENT_TYPE_NOT_ALLOWED);
        }

        @Test
        @DisplayName("a damaged .docx with DOCUMENT_UNREADABLE")
        void damaged_docx() {
            byte[] broken = {'P', 'K', 3, 4, 1, 2, 3, 4, 5, 6, 7, 8, 9};

            assertThatThrownBy(() -> extractor.extract(ClientDocumentFile.of("roto.docx", null, broken)))
                    .isInstanceOf(DomainException.class)
                    .extracting(e -> ((DomainException) e).error())
                    .isIn(WorkspaceError.DOCUMENT_UNREADABLE, WorkspaceError.DOCUMENT_TYPE_NOT_ALLOWED);
        }
    }

    @Test
    @DisplayName("normalizes text: no NUL or control characters, tidy line breaks")
    void normalizes_text() {
        assertThat(PdfWordTextExtractor.normalize("  Hola\u0000 mundo \r\n\r\n\r\n\r\nSegunda\u0007 línea\t \n"))
                .isEqualTo("Hola mundo\n\nSegunda línea");
        assertThat(PdfWordTextExtractor.normalize("a\uD800b")).isEqualTo("ab");
    }

    private void assertError(byte[] content, String fileName, WorkspaceError expected) {
        assertThatThrownBy(() -> extractor.extract(ClientDocumentFile.of(fileName, null, content)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).error())
                .isEqualTo(expected);
    }

    private static byte[] zipWithContentTypes(String mainContentType) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write(contentTypes(mainContentType));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<w:document/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    private static byte[] contentTypes(String mainContentType) {
        return ("<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Override PartName=\"/word/document.xml\" ContentType=\"" + mainContentType + "\"/></Types>")
                .getBytes(StandardCharsets.UTF_8);
    }
}
