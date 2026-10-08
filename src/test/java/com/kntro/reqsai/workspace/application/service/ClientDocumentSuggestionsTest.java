package com.kntro.reqsai.workspace.application.service;

import com.kntro.reqsai.workspace.application.port.DocumentClassification;
import com.kntro.reqsai.workspace.application.port.DocumentClassification.SuggestedTerm;
import com.kntro.reqsai.workspace.domain.model.DocumentType;
import com.kntro.reqsai.workspace.domain.model.ProjectDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Application: ClientDocumentSuggestions (normalize the AI classification)")
class ClientDocumentSuggestionsTest {

    @Test
    @DisplayName("trims, collapses whitespace and drops blank or case-insensitive duplicate suggestions")
    void cleans_and_dedupes() {
        List<SuggestedTerm> terms = new ArrayList<>();
        terms.add(new SuggestedTerm("  Comensal ", " Cliente   que reserva. "));
        terms.add(new SuggestedTerm("COMENSAL", "Otra definición"));
        terms.add(new SuggestedTerm(" ", "Sin término"));
        terms.add(new SuggestedTerm("Reserva", "  "));
        terms.add(new SuggestedTerm("Mesa", "Lugar del salón."));
        DocumentClassification raw = new DocumentClassification("  Resumen\n del  cliente. ", DocumentType.BUSINESS_RULES,
                terms, List.of(" Ley 29733 ", "ley   29733", "", "Disponibilidad 99,5 %"));

        DocumentClassification result = ClientDocumentSuggestions.normalize(raw);

        assertThat(result.summary()).isEqualTo("Resumen del cliente.");
        assertThat(result.documentType()).isEqualTo(DocumentType.BUSINESS_RULES);
        assertThat(result.glossary()).containsExactly(
                new SuggestedTerm("Comensal", "Cliente que reserva."),
                new SuggestedTerm("Mesa", "Lugar del salón."));
        assertThat(result.constraints()).containsExactly("Ley 29733", "Disponibilidad 99,5 %");
    }

    @Test
    @DisplayName("keeps lengths within what the glossary and constraint forms accept, and caps list sizes")
    void caps_lengths_and_counts() {
        List<SuggestedTerm> terms = IntStream.range(0, 60)
                .mapToObj(i -> new SuggestedTerm("Término " + i, "palabra ".repeat(300)))
                .toList();
        List<SuggestedTerm> withLongTerm = new ArrayList<>(List.of(new SuggestedTerm("t".repeat(201), "def")));
        withLongTerm.addAll(terms);
        List<String> constraints = IntStream.range(0, 50).mapToObj(i -> "restricción " + i + " " + "x ".repeat(400)).toList();

        DocumentClassification result = ClientDocumentSuggestions.normalize(new DocumentClassification(
                "a ".repeat(3000), null, withLongTerm, constraints));

        assertThat(result.summary()).hasSizeLessThanOrEqualTo(ProjectDocument.SUMMARY_MAX).endsWith("…");
        assertThat(result.glossary()).hasSize(ClientDocumentSuggestions.MAX_TERMS);
        assertThat(result.glossary().getFirst().term()).isEqualTo("Término 0");
        assertThat(result.glossary()).allSatisfy(t ->
                assertThat(t.definition()).hasSizeLessThanOrEqualTo(ClientDocumentSuggestions.DEFINITION_MAX));
        assertThat(result.constraints()).hasSize(ClientDocumentSuggestions.MAX_CONSTRAINTS)
                .allSatisfy(c -> assertThat(c).hasSizeLessThanOrEqualTo(ClientDocumentSuggestions.CONSTRAINT_MAX));
    }

    @Test
    @DisplayName("an empty classification stays empty")
    void empty() {
        DocumentClassification result = ClientDocumentSuggestions.normalize(DocumentClassification.empty());

        assertThat(result.summary()).isNull();
        assertThat(result.glossary()).isEmpty();
        assertThat(result.constraints()).isEmpty();
    }

    @Test
    @DisplayName("sends at most the first 60,000 characters to the model, cut on a word boundary")
    void model_input_is_capped() {
        String shortText = "Hola mundo";
        String longText = "palabra ".repeat(10_000);

        assertThat(ClientDocumentSuggestions.modelInput(shortText)).isSameAs(shortText);
        String input = ClientDocumentSuggestions.modelInput(longText);
        assertThat(input).hasSizeLessThanOrEqualTo(ClientDocumentSuggestions.MODEL_INPUT_MAX_CHARS).endsWith("palabra");
    }

    @Test
    @DisplayName("falls back to a single-line excerpt of the document as its summary")
    void fallback_summary() {
        assertThat(ClientDocumentSuggestions.fallbackSummary("Línea uno\n\nLínea   dos")).isEqualTo("Línea uno Línea dos");
        assertThat(ClientDocumentSuggestions.fallbackSummary("texto ".repeat(500)))
                .hasSizeLessThanOrEqualTo(ClientDocumentSuggestions.FALLBACK_SUMMARY_MAX)
                .endsWith("…");
    }

    @Test
    @DisplayName("duplicate keys ignore case and inner spacing")
    void key() {
        assertThat(ClientDocumentSuggestions.key("  Tasa   de Cambio ")).isEqualTo("tasa de cambio");
    }
}
