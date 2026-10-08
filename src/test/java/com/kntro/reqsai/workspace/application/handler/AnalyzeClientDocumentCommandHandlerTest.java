package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.command.AnalyzeClientDocumentCommand;
import com.kntro.reqsai.workspace.application.port.DocumentClassification;
import com.kntro.reqsai.workspace.application.port.DocumentClassification.SuggestedTerm;
import com.kntro.reqsai.workspace.application.port.DocumentClassificationPort;
import com.kntro.reqsai.workspace.application.port.DocumentClassificationRequest;
import com.kntro.reqsai.workspace.application.port.DocumentTextExtractor;
import com.kntro.reqsai.workspace.application.port.ExtractedText;
import com.kntro.reqsai.workspace.application.result.ClientDocumentAnalysis;
import com.kntro.reqsai.workspace.application.service.ClientDocumentSuggestions;
import com.kntro.reqsai.workspace.application.service.PendingDocumentService;
import com.kntro.reqsai.workspace.application.service.PendingDocumentService.ProjectKnowledge;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceError;
import com.kntro.reqsai.workspace.domain.model.DocumentStatus;
import com.kntro.reqsai.workspace.domain.model.DocumentType;
import com.kntro.reqsai.workspace.domain.model.ProjectDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Application: Analyze client document (US22)")
@ExtendWith(MockitoExtension.class)
class AnalyzeClientDocumentCommandHandlerTest {

    private static final byte[] PDF = "%PDF-1.7 fake".getBytes(StandardCharsets.US_ASCII);
    private static final UUID ORG = UUID.randomUUID();
    private static final UUID PROJECT = UUID.randomUUID();
    private static final ProjectKnowledge KNOWLEDGE = new ProjectKnowledge("Restaurante", "Gastronomía",
            Set.of("comensal"), Set.of("debe cumplir la ley 29733."));

    @Mock private PendingDocumentService pendingDocuments;
    @Mock private DocumentTextExtractor extractor;
    @Mock private DocumentClassificationPort classifier;
    @InjectMocks private AnalyzeClientDocumentCommandHandler handler;

    @BeforeEach
    void storePendingAsIs() {
        lenient().when(pendingDocuments.savePending(any(ProjectDocument.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("extracts, classifies and stores a PENDING document, flagging what the project already has")
    void classifies_and_flags_existing() {
        when(pendingDocuments.preflight(ORG, PROJECT)).thenReturn(KNOWLEDGE);
        when(extractor.extract(any())).thenReturn(new ExtractedText("Texto del cliente sobre reservas.", false));
        when(classifier.isAvailable()).thenReturn(true);
        when(classifier.classify(any())).thenReturn(new DocumentClassification("Resumen del cliente.",
                DocumentType.BUSINESS_RULES,
                List.of(new SuggestedTerm("Comensal", "Cliente."), new SuggestedTerm("Reserva", "Mesa apartada.")),
                List.of("Debe cumplir la Ley 29733.", "Disponible 24/7.")));

        ClientDocumentAnalysis analysis = handler.handle(command("TdR.pdf", PDF));

        assertThat(analysis.classified()).isTrue();
        assertThat(analysis.truncated()).isFalse();
        assertThat(analysis.summary()).isEqualTo("Resumen del cliente.");
        assertThat(analysis.suggestedType()).isEqualTo(DocumentType.BUSINESS_RULES);
        assertThat(analysis.glossary()).extracting(ClientDocumentAnalysis.GlossarySuggestion::term,
                        ClientDocumentAnalysis.GlossarySuggestion::exists)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Comensal", true),
                        org.assertj.core.groups.Tuple.tuple("Reserva", false));
        assertThat(analysis.constraints()).extracting(ClientDocumentAnalysis.ConstraintSuggestion::exists)
                .containsExactly(true, false);

        ProjectDocument document = analysis.document();
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(document.getProjectId()).isEqualTo(PROJECT);
        assertThat(document.getDocumentType()).isEqualTo(DocumentType.BUSINESS_RULES);
        assertThat(document.extractedText()).contains("Texto del cliente sobre reservas.");

        ArgumentCaptor<DocumentClassificationRequest> request = ArgumentCaptor.forClass(DocumentClassificationRequest.class);
        verify(classifier).classify(request.capture());
        assertThat(request.getValue().projectName()).isEqualTo("Restaurante");
        assertThat(request.getValue().fileName()).isEqualTo("TdR.pdf");
        assertThat(request.getValue().truncated()).isFalse();
    }

    @Test
    @DisplayName("sends only the first 60,000 characters of a long document to the model")
    void caps_model_input() {
        String longText = "palabra ".repeat(20_000);
        when(pendingDocuments.preflight(ORG, PROJECT)).thenReturn(KNOWLEDGE);
        when(extractor.extract(any())).thenReturn(new ExtractedText(longText.strip(), true));
        when(classifier.isAvailable()).thenReturn(true);
        when(classifier.classify(any())).thenReturn(DocumentClassification.empty());

        ClientDocumentAnalysis analysis = handler.handle(command("largo.pdf", PDF));

        ArgumentCaptor<DocumentClassificationRequest> request = ArgumentCaptor.forClass(DocumentClassificationRequest.class);
        verify(classifier).classify(request.capture());
        assertThat(request.getValue().text()).hasSizeLessThanOrEqualTo(ClientDocumentSuggestions.MODEL_INPUT_MAX_CHARS);
        assertThat(request.getValue().truncated()).isTrue();
        assertThat(analysis.truncated()).isTrue();
        assertThat(analysis.classified()).isTrue();
        assertThat(analysis.suggestedType()).isEqualTo(DocumentType.REFERENCE);
    }

    @Test
    @DisplayName("without an AI model the document is still stored, unclassified, with an excerpt as summary")
    void unavailable_model_falls_back() {
        when(pendingDocuments.preflight(ORG, PROJECT)).thenReturn(KNOWLEDGE);
        when(extractor.extract(any())).thenReturn(new ExtractedText("Acta de la reunión\ncon el cliente.", false));
        when(classifier.isAvailable()).thenReturn(false);

        ClientDocumentAnalysis analysis = handler.handle(command("acta.pdf", PDF));

        assertThat(analysis.classified()).isFalse();
        assertThat(analysis.summary()).isEqualTo("Acta de la reunión con el cliente.");
        assertThat(analysis.glossary()).isEmpty();
        assertThat(analysis.constraints()).isEmpty();
        assertThat(analysis.suggestedType()).isEqualTo(DocumentType.REFERENCE);
        verify(classifier, never()).classify(any());
        verify(pendingDocuments, times(1)).savePending(any());
    }

    @Test
    @DisplayName("a failing model does not fail the upload")
    void failing_model_falls_back() {
        when(pendingDocuments.preflight(ORG, PROJECT)).thenReturn(KNOWLEDGE);
        when(extractor.extract(any())).thenReturn(new ExtractedText("Texto", false));
        when(classifier.isAvailable()).thenReturn(true);
        when(classifier.classify(any())).thenThrow(new IllegalStateException("model down"));

        ClientDocumentAnalysis analysis = handler.handle(command("acta.pdf", PDF));

        assertThat(analysis.classified()).isFalse();
        assertThat(analysis.summary()).isEqualTo("Texto");
    }

    @Test
    @DisplayName("an executable is rejected before the project is read or any text is extracted")
    void rejects_executable_first() {
        byte[] exe = {'M', 'Z', 0, 0};

        assertThatThrownBy(() -> handler.handle(command("setup.exe", exe)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).error())
                .isEqualTo(WorkspaceError.DOCUMENT_TYPE_NOT_ALLOWED);
        assertThatThrownBy(() -> handler.handle(command("factura.pdf", exe)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).error())
                .isEqualTo(WorkspaceError.DOCUMENT_TYPE_NOT_ALLOWED);
        verifyNoInteractions(pendingDocuments, extractor, classifier);
    }

    private static AnalyzeClientDocumentCommand command(String fileName, byte[] content) {
        return new AnalyzeClientDocumentCommand(ORG, PROJECT, fileName, "application/octet-stream", content,
                UUID.randomUUID());
    }
}
