package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Suggestion code subject")
class SuggestionCodeSubjectTest {

    @Test
    @DisplayName("a story is described with its narrative, its criteria values and what was said")
    void story() {
        Suggestion s = Suggestion.newStory(UUID.randomUUID(), UUID.randomUUID(), "Cancelar reserva", "comensal",
                "cancelar mi reserva hasta 24 horas antes", "no pagar por una mesa", Priority.HIGH, 3,
                List.of(new Suggestion.DraftCriterion(null, "una reserva", "se cancela con 24 horas", "no se cobra")));
        s.annotate(1, "hasta 24 horas antes", null, null, null);

        String subject = SuggestionCodeSubject.of(s);

        assertThat(subject).contains("Cancelar reserva", "Role: comensal.", "Action: cancelar mi reserva hasta 24 horas antes",
                "Given una reserva, when se cancela con 24 horas, then no se cobra",
                "Said in the conversation: hasta 24 horas antes");
    }

    @Test
    @DisplayName("a clarifying question is the question itself")
    void question() {
        Suggestion s = Suggestion.clarifyingQuestion(UUID.randomUUID(), UUID.randomUUID(), "¿Se cobra penalidad?");

        assertThat(SuggestionCodeSubject.of(s)).isEqualTo("¿Se cobra penalidad?");
    }
}
