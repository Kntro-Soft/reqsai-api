package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.domain.event.SuggestionCreatedEvent;
import com.kntro.reqsai.discovery.domain.model.CodeFinding;
import com.kntro.reqsai.discovery.domain.model.CodeReference;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Evidence and code insight are attached right after the factory: the creation event — what the live card is
 * built from — must be replaced by one that carries them, and must not be duplicated.
 */
@DisplayName("Suggestion annotation")
class SuggestionAnnotationTest {

    @Test
    @DisplayName("the creation event carries the evidence and the code insight attached after the factory")
    void replacesCreationEvent() {
        Suggestion s = Suggestion.newStory(UUID.randomUUID(), UUID.randomUUID(), "Cancelar reserva", "comensal",
                "cancelar mi reserva", "no perder dinero", Priority.HIGH, 3);

        s.annotate(5, "cancelar hasta 24 horas antes", CodeFinding.CONFLICTS_WITH_CODE,
                "El código permite 2 h; el cliente pide 24 h",
                List.of(new CodeReference("acme/reservas", "src/reservations", "Reservas", null)));

        Collection<?> events = events(s);
        assertThat(events).hasSize(1);
        SuggestionCreatedEvent created = (SuggestionCreatedEvent) events.iterator().next();
        assertThat(created.evidenceSequence()).isEqualTo(5);
        assertThat(created.evidenceQuote()).isEqualTo("cancelar hasta 24 horas antes");
        assertThat(created.codeFinding()).isEqualTo(CodeFinding.CONFLICTS_WITH_CODE);
        assertThat(created.codeReferences()).singleElement().extracting(CodeReference::path).isEqualTo("src/reservations");
    }

    @Test
    @DisplayName("a note without a finding is dropped, and no evidence means no segment")
    void normalizes() {
        Suggestion s = Suggestion.clarifyingQuestion(UUID.randomUUID(), UUID.randomUUID(), "¿Se cobra penalidad?");
        s.annotate(9, "  ", null, "nota suelta", null);
        assertThat(s.getEvidenceQuote()).isNull();
        assertThat(s.getEvidenceSequence()).isNull();
        assertThat(s.getCodeNote()).isNull();
        assertThat(s.getCodeReferences()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static Collection<Object> events(Suggestion s) {
        return (Collection<Object>) ReflectionTestUtils.invokeMethod(s, "domainEvents");
    }
}
