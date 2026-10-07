package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.GenerationResult;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.SuggestionType;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SuggestionTitles}: a story kept from a draft linked to a pending suggestion never
 * keeps that suggestion's title, and a NEW_STORY never takes a title already in the queue.
 */
@DisplayName("Application: SuggestionTitles")
class SuggestionTitlesTest {

    private static final SuggestionDedupPolicy.Draft BOOKING = new SuggestionDedupPolicy.Draft(
            SuggestionType.NEW_STORY, "Reserva de cita médica", "paciente",
            "reservar una cita médica desde el portal web", "ser atendido a tiempo", List.of());

    private static GenerationResult.GeneratedStory draft(String title, String action, @Nullable String scenario,
                                                         String then) {
        return new GenerationResult.GeneratedStory(SuggestionType.EDGE_CASE, title, "paciente", action,
                "se respeten los horarios", Priority.HIGH, 2,
                List.of(new GenerationResult.GeneratedCriterion(scenario, "una cita reservada",
                        "el paciente la cancela con menos de 24 horas", then)),
                null, UUID.randomUUID());
    }

    @Test
    @DisplayName("a linked draft keeps its own title when it names something the linked title does not")
    void keeps_a_distinct_own_title() {
        SuggestionTitles titles = new SuggestionTitles();
        titles.reserve(BOOKING);

        String title = titles.forLinkedDraft(draft("Reserva de cita médica con penalidad",
                "cancelar una cita", "Cancelación tardía", "se cobra una penalidad"), BOOKING);

        assertThat(title).isEqualTo("Reserva de cita médica con penalidad");
    }

    @Test
    @DisplayName("a linked draft titled like the linked suggestion (any inflection) is titled from its scenario")
    void replaces_a_reused_title_with_the_scenario() {
        SuggestionTitles titles = new SuggestionTitles();
        titles.reserve(BOOKING);

        assertThat(titles.forLinkedDraft(draft("Reservar citas médicas", "cancelar una cita",
                "Penalidad por cancelación tardía", "se cobra una penalidad"), BOOKING))
                .isEqualTo("Penalidad por cancelación tardía");
        assertThat(titles.forLinkedDraft(draft("Cita médica", "cancelar una cita",
                "Penalidad por cancelación tardía", "se cobra una penalidad"), BOOKING))
                .isEqualTo("Penalidad por cancelación tardía");
    }

    @Test
    @DisplayName("a content candidate that only repeats the linked suggestion is skipped")
    void skips_candidates_that_say_nothing_new() {
        SuggestionTitles titles = new SuggestionTitles();
        titles.reserve(BOOKING);

        // The scenario and the action only repeat the booking; the Then states the rule.
        String title = titles.forLinkedDraft(draft("Reserva de cita médica", "reservar una cita médica",
                "Reserva de cita", "se le cobra una penalidad del 10 %."), BOOKING);

        assertThat(title).isEqualTo("Se le cobra una penalidad del 10 %");
    }

    @Test
    @DisplayName("a linked draft that differs only in its actor is titled by its action and role")
    void titles_by_action_and_role() {
        SuggestionTitles titles = new SuggestionTitles();
        titles.reserve(BOOKING);
        GenerationResult.GeneratedStory receptionist = new GenerationResult.GeneratedStory(
                SuggestionType.UPDATE_STORY, "Reserva de cita médica", "recepcionista",
                "reservar una cita médica desde el portal web", "ser atendido a tiempo", Priority.HIGH, 3,
                List.of(), null, UUID.randomUUID());

        assertThat(titles.forLinkedDraft(receptionist, BOOKING))
                .isEqualTo("Reservar una cita médica desde el portal web (recepcionista)");
    }

    @Test
    @DisplayName("a linked draft with nothing to title it by gets a numbered title, never the linked one")
    void falls_back_to_a_numbered_title() {
        SuggestionTitles titles = new SuggestionTitles();
        titles.reserve(BOOKING);
        GenerationResult.GeneratedStory bare = new GenerationResult.GeneratedStory(SuggestionType.UPDATE_STORY,
                "Reserva de cita médica", "paciente", "reserva de cita médica", "ser atendido", Priority.HIGH, 3,
                List.of(), null, UUID.randomUUID());

        assertThat(titles.forLinkedDraft(bare, BOOKING)).isEqualTo("Reserva de cita médica (2)");
    }

    @Test
    @DisplayName("a NEW_STORY keeps a free title, and gets one of its own when the title is taken")
    void unique_new_story_title() {
        SuggestionTitles titles = new SuggestionTitles();
        GenerationResult.GeneratedStory notification = draft("Reserva de cita médica",
                "recibir un correo cuando un paciente reserva una cita", null, "el médico recibe un correo");

        assertThat(titles.uniqueNewStoryTitle(notification)).isEqualTo("Reserva de cita médica");

        titles.reserve(BOOKING);
        assertThat(titles.uniqueNewStoryTitle(notification))
                .isEqualTo("Recibir un correo cuando un paciente reserva una cita");
    }

    @Test
    @DisplayName("a derived title already taken is passed over for the next candidate")
    void derived_title_is_unique_too() {
        SuggestionTitles titles = new SuggestionTitles();
        titles.reserve(BOOKING);
        titles.reserve(new SuggestionDedupPolicy.Draft(SuggestionType.NEW_STORY, "Penalidad por cancelación tardía",
                "paciente", "cancelar una cita", "se respeten los horarios", List.of()));

        String title = titles.forLinkedDraft(draft("Reserva de cita médica", "pagar una multa al cancelar tarde",
                "Penalidad por cancelación tardía", "se cobra una penalidad"), BOOKING);

        assertThat(title).isEqualTo("Pagar una multa al cancelar tarde");
    }

    @Test
    @DisplayName("asTitle: single-spaced, trimmed, capitalized and cut at a word boundary")
    void as_title_formats_text() {
        assertThat(SuggestionTitles.asTitle("  «notificar   al médico»;  ")).isEqualTo("Notificar al médico");
        assertThat(SuggestionTitles.asTitle("¿éxito?")).isEqualTo("Éxito");
        assertThat(SuggestionTitles.asTitle("cobrar el 10 %")).isEqualTo("Cobrar el 10 %");
        assertThat(SuggestionTitles.asTitle("  ")).isNull();
        assertThat(SuggestionTitles.asTitle(null)).isNull();

        String longAction = "palabra ".repeat(30).strip();
        assertThat(SuggestionTitles.asTitle(longAction))
                .hasSizeLessThanOrEqualTo(SuggestionTitles.DERIVED_TITLE_MAX)
                .startsWith("Palabra")
                .endsWith("palabra");
    }

    @Test
    @DisplayName("reusesTitle: true only when the title names nothing the linked title does not")
    void reuses_title() {
        assertThat(SuggestionTitles.reusesTitle("Reservar citas médicas", "Reserva de cita médica")).isTrue();
        assertThat(SuggestionTitles.reusesTitle("Cita médica", "Reserva de cita médica")).isTrue();
        assertThat(SuggestionTitles.reusesTitle("de la", "Reserva de cita médica")).isTrue();
        assertThat(SuggestionTitles.reusesTitle("Penalidad por cancelación tardía", "Reserva de cita médica"))
                .isFalse();
    }
}
