package com.kntro.reqsai.workspace.infrastructure.documents;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.port.DocumentTextExtractor;
import com.kntro.reqsai.workspace.application.port.ExtractedText;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.ProjectDocumentContent;
import com.kntro.reqsai.workspace.domain.valueobjects.ClientDocumentFile;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * {@link DocumentTextExtractor} backed by Apache PDFBox (PDF) and Apache POI (Word {@code .docx}).
 * <p>
 * Hostile input is bounded before and during parsing:
 * <ul>
 *   <li>a {@code .docx} is first streamed through {@link ZipInputStream}, counting the bytes that really
 *       inflate, so a zip bomb (or a package with an absurd number of entries) is rejected before POI
 *       loads it; its {@code [Content_Types].xml} must declare a WordprocessingML document (an Excel file
 *       or a macro-enabled {@code .docm} renamed to {@code .docx} is not accepted);</li>
 *   <li>PDF streams are decoded into a bounded memory cache that spills to a temporary file, and text is
 *       read page by page, stopping once the cap is reached or after {@link #MAX_PDF_PAGES} pages;</li>
 *   <li>the text is normalized (control characters, which PostgreSQL rejects, removed; blank lines
 *       collapsed) and capped at {@link ProjectDocumentContent#MAX_CHARS} characters.</li>
 * </ul>
 */
@Component
@Slf4j
public class PdfWordTextExtractor implements DocumentTextExtractor {

    static final int MAX_PDF_PAGES = 2_000;
    static final int MAX_ZIP_ENTRIES = 5_000;
    /** Total bytes a {@code .docx} may inflate to (text, styles and images included). */
    static final long MAX_ZIP_INFLATED_BYTES = 200L * 1024 * 1024;
    private static final long PDF_MAIN_MEMORY_BYTES = 32L * 1024 * 1024;
    private static final int CONTENT_TYPES_MAX_BYTES = 1024 * 1024;

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F\\uFFFE\\uFFFF]");
    private static final Pattern LONE_SURROGATES = Pattern.compile(
            "[\\uD800-\\uDBFF](?![\\uDC00-\\uDFFF])|(?<![\\uD800-\\uDBFF])[\\uDC00-\\uDFFF]");
    private static final Pattern TRAILING_SPACES = Pattern.compile("[ \\t\\u00A0]+\\n");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");

    /**
     * POI rejects any part that inflates more than 100x as a zip bomb, which also refuses legitimate but
     * repetitive documents (long tables, repeated styles). The real inflated size is already capped by
     * {@link #inspectPackage}, so the ratio heuristic is relaxed to 1000x.
     */
    static final double MIN_INFLATE_RATIO = 0.001;

    static {
        ZipSecureFile.setMinInflateRatio(MIN_INFLATE_RATIO);
    }

    @Override
    public ExtractedText extract(ClientDocumentFile file) {
        String raw = switch (file.format()) {
            case PDF -> pdfText(file.content());
            case DOCX -> docxText(file.content());
        };
        String text = normalize(raw);
        if (text.isEmpty()) {
            throw WorkspaceExceptions.documentEmpty(
                    "No text could be extracted from the document (a scanned document holds only images)");
        }
        if (text.length() <= ProjectDocumentContent.MAX_CHARS) {
            return new ExtractedText(text, false);
        }
        int end = ProjectDocumentContent.MAX_CHARS;
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return new ExtractedText(text.substring(0, end).strip(), true);
    }

    private static String pdfText(byte[] content) {
        try (PDDocument pdf = Loader.loadPDF(content, "", null, null,
                MemoryUsageSetting.setupMixed(PDF_MAIN_MEMORY_BYTES).streamCache)) {
            PDFTextStripper stripper = new PDFTextStripper();
            int pages = Math.min(pdf.getNumberOfPages(), MAX_PDF_PAGES);
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= pages && text.length() <= ProjectDocumentContent.MAX_CHARS * 2L; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                text.append(stripper.getText(pdf)).append('\n');
            }
            return text.toString();
        } catch (InvalidPasswordException e) {
            throw WorkspaceExceptions.documentUnreadable("the PDF is password-protected");
        } catch (IOException | RuntimeException e) {
            if (e instanceof DomainException domain) {
                throw domain;
            }
            log.info("PDF text extraction failed: {}", e.toString());
            throw WorkspaceExceptions.documentUnreadable("the PDF is damaged or malformed");
        }
    }

    private static String docxText(byte[] content) {
        inspectPackage(content);
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        } catch (IOException | RuntimeException e) {
            log.info("Word text extraction failed: {}", e.toString());
            throw WorkspaceExceptions.documentUnreadable("the Word document is damaged or malformed");
        }
    }

    /**
     * Streams every entry of the {@code .docx} package, rejecting it when it inflates beyond
     * {@link #MAX_ZIP_INFLATED_BYTES}, holds more than {@link #MAX_ZIP_ENTRIES} entries, or is not a
     * WordprocessingML package.
     */
    static void inspectPackage(byte[] content) {
        boolean wordprocessing = false;
        long inflated = 0;
        int entries = 0;
        byte[] buffer = new byte[64 * 1024];
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ZIP_ENTRIES) {
                    throw WorkspaceExceptions.documentUnreadable("the Word package has too many parts");
                }
                if ("[Content_Types].xml".equals(entry.getName())) {
                    String types = readCapped(zip, CONTENT_TYPES_MAX_BYTES);
                    inflated += types.length();
                    wordprocessing = types.contains("wordprocessingml.document.main+xml")
                            || types.contains("wordprocessingml.template.main+xml");
                    continue;
                }
                int read;
                while ((read = zip.read(buffer)) > 0) {
                    inflated += read;
                    if (inflated > MAX_ZIP_INFLATED_BYTES) {
                        throw WorkspaceExceptions.documentUnreadable("the Word document expands beyond the allowed size");
                    }
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            throw WorkspaceExceptions.documentUnreadable("the Word document is damaged or malformed");
        }
        if (entries == 0) {
            throw WorkspaceExceptions.documentUnreadable("the Word document is damaged or malformed");
        }
        if (!wordprocessing) {
            throw WorkspaceExceptions.documentTypeNotAllowed("the file is not a Word (.docx) document");
        }
    }

    private static String readCapped(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8 * 1024];
        int read;
        while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
            if (out.size() > max) {
                throw WorkspaceExceptions.documentUnreadable("the Word document is damaged or malformed");
            }
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    /** Removes characters PostgreSQL text cannot hold and tidies whitespace, keeping line structure. */
    static String normalize(String raw) {
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        text = CONTROL_CHARS.matcher(text).replaceAll("");
        text = LONE_SURROGATES.matcher(text).replaceAll("");
        text = TRAILING_SPACES.matcher(text).replaceAll("\n");
        text = BLANK_LINES.matcher(text).replaceAll("\n\n");
        return text.strip();
    }
}
