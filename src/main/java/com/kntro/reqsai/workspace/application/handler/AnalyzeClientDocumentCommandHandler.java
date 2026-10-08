package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.AnalyzeClientDocumentCommand;
import com.kntro.reqsai.workspace.application.port.DocumentClassification;
import com.kntro.reqsai.workspace.application.port.DocumentClassificationPort;
import com.kntro.reqsai.workspace.application.port.DocumentClassificationRequest;
import com.kntro.reqsai.workspace.application.port.DocumentTextExtractor;
import com.kntro.reqsai.workspace.application.port.ExtractedText;
import com.kntro.reqsai.workspace.application.result.ClientDocumentAnalysis;
import com.kntro.reqsai.workspace.application.result.ClientDocumentAnalysis.ConstraintSuggestion;
import com.kntro.reqsai.workspace.application.result.ClientDocumentAnalysis.GlossarySuggestion;
import com.kntro.reqsai.workspace.application.service.ClientDocumentSuggestions;
import com.kntro.reqsai.workspace.application.service.PendingDocumentService;
import com.kntro.reqsai.workspace.application.service.PendingDocumentService.ProjectKnowledge;
import com.kntro.reqsai.workspace.domain.model.DocumentType;
import com.kntro.reqsai.workspace.domain.model.ProjectDocument;
import com.kntro.reqsai.workspace.domain.valueobjects.ClientDocumentFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Uploads a client document (US22): validates the file (type by extension, declared content type and
 * magic bytes; size; emptiness), extracts its text, asks the AI to classify it into a context summary,
 * glossary terms and constraints, and stores it as a {@code PENDING} document the analyst then reviews
 * and applies ({@link ApplyClientDocumentCommandHandler}).
 * <p>
 * Deliberately not transactional: extraction and classification can take seconds, so the database is
 * touched only in the short {@link PendingDocumentService} transactions before and after them. When no
 * model is configured or the model fails, the document is still stored with an excerpt as its summary
 * and empty suggestions ({@code classified = false}), so the analyst can keep it as context anyway.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyzeClientDocumentCommandHandler {

    private final PendingDocumentService pendingDocuments;
    private final DocumentTextExtractor extractor;
    private final DocumentClassificationPort classifier;

    public ClientDocumentAnalysis handle(AnalyzeClientDocumentCommand command) {
        ClientDocumentFile file = ClientDocumentFile.of(command.fileName(), command.contentType(), command.content());
        ProjectKnowledge knowledge = pendingDocuments.preflight(command.organizationId(), command.projectId());

        ExtractedText extracted = extractor.extract(file);
        Optional<DocumentClassification> classification = classify(file, knowledge, extracted.text());
        DocumentClassification suggestions = classification
                .map(ClientDocumentSuggestions::normalize)
                .orElseGet(DocumentClassification::empty);

        DocumentType type = suggestions.documentType() != null ? suggestions.documentType() : DocumentType.REFERENCE;
        ProjectDocument document = pendingDocuments.savePending(ProjectDocument.pendingUpload(
                command.projectId(), file, type, extracted.text(), extracted.truncated()));

        String summary = suggestions.summary() != null
                ? suggestions.summary()
                : ClientDocumentSuggestions.fallbackSummary(extracted.text());
        log.info("Client document '{}' analyzed for project {}: {} chars, classified={}, {} term(s), {} constraint(s)",
                file.fileName(), command.projectId(), extracted.text().length(), classification.isPresent(),
                suggestions.glossary().size(), suggestions.constraints().size());

        return new ClientDocumentAnalysis(
                document,
                classification.isPresent(),
                extracted.truncated(),
                summary,
                type,
                suggestions.glossary().stream()
                        .map(t -> new GlossarySuggestion(t.term(), t.definition(), knowledge.hasTerm(t.term())))
                        .toList(),
                suggestions.constraints().stream()
                        .map(c -> new ConstraintSuggestion(c, knowledge.hasConstraint(c)))
                        .toList());
    }

    /** The model's classification, or empty when no model is configured or it failed. */
    private Optional<DocumentClassification> classify(ClientDocumentFile file, ProjectKnowledge knowledge, String text) {
        if (!classifier.isAvailable()) {
            log.info("Document classification skipped for '{}': no AI model is configured", file.fileName());
            return Optional.empty();
        }
        String input = ClientDocumentSuggestions.modelInput(text);
        try {
            return Optional.of(classifier.classify(new DocumentClassificationRequest(
                    file.fileName(), knowledge.projectName(), knowledge.projectDomain(), input,
                    input.length() < text.length())));
        } catch (RuntimeException e) {
            log.warn("Document classification failed for '{}'; storing it unclassified: {}",
                    file.fileName(), e.getMessage());
            return Optional.empty();
        }
    }
}
