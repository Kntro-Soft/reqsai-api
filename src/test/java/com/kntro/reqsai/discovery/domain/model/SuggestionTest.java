package com.kntro.reqsai.discovery.domain.model;

import com.kntro.reqsai.discovery.domain.event.SuggestionCreatedEvent;
import com.kntro.reqsai.testsupport.AggregateEvents;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link Suggestion}: the draft acceptance-criteria sanitizing that carries the LLM's
 * proposed Given/When/Then criteria through the review gate, and the {@link SuggestionCreatedEvent}
 * each factory registers (the snapshot the live {@code SUGGESTION_GENERATED} message is built from).
 */
@DisplayName("Domain: Suggestion")
class SuggestionTest {

    private final UUID sessionId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    private Suggestion newStoryWith(List<Suggestion.DraftCriterion> criteria) {
        return Suggestion.newStory(sessionId, projectId,
                "Iniciar sesión", "usuario", "iniciar sesión", "acceder", Priority.HIGH, 3, criteria);
    }

    @Test
    @DisplayName("should preserve structured criteria in order, with null scenario when absent")
    void should_round_trip_criteria() {
        Suggestion s = newStoryWith(List.of(
                new Suggestion.DraftCriterion("Válido", "tiene cuenta", "ingresa bien", "accede"),
                new Suggestion.DraftCriterion(null, "clave mala", "intenta", "ve error")));

        List<Suggestion.DraftCriterion> decoded = s.getDraftAcceptanceCriteria();

        assertThat(decoded).hasSize(2);
        assertThat(decoded.getFirst().scenario()).isEqualTo("Válido");
        assertThat(decoded.getFirst().given()).isEqualTo("tiene cuenta");
        assertThat(decoded.getLast().scenario()).isNull();
        assertThat(decoded.getLast().then()).isEqualTo("ve error");
    }

    @Test
    @DisplayName("should drop a criterion missing given/when/then rather than encode a broken row")
    void should_drop_incomplete_criterion() {
        Suggestion s = newStoryWith(List.of(
                new Suggestion.DraftCriterion("ok", "g", "w", "t"),
                new Suggestion.DraftCriterion("incompleta", "", "w", "t")));

        assertThat(s.getDraftAcceptanceCriteria()).hasSize(1);
        assertThat(s.getDraftAcceptanceCriteria().getFirst().scenario()).isEqualTo("ok");
    }

    @Test
    @DisplayName("should strip surrounding whitespace and normalize a blank scenario to null")
    void should_strip_and_normalize_scenario() {
        Suggestion s = newStoryWith(List.of(
                new Suggestion.DraftCriterion("   ", "  tiene cuenta  ", "ingresa", "accede")));

        Suggestion.DraftCriterion c = s.getDraftAcceptanceCriteria().getFirst();
        assertThat(c.scenario()).isNull();
        assertThat(c.given()).isEqualTo("tiene cuenta");
    }

    @Test
    @DisplayName("should truncate an over-long scenario to 200 chars so accept is not fatal")
    void should_truncate_over_long_scenario() {
        String longScenario = "x".repeat(250);
        Suggestion s = newStoryWith(List.of(
                new Suggestion.DraftCriterion(longScenario, "tiene cuenta", "ingresa", "accede")));

        Suggestion.DraftCriterion c = s.getDraftAcceptanceCriteria().getFirst();
        assertThat(c.scenario()).hasSize(200);
        assertThat(c.scenario()).isEqualTo("x".repeat(200));
    }

    @Test
    @DisplayName("should carry no criteria for the no-criteria factory")
    void should_be_empty_without_criteria() {
        Suggestion s = Suggestion.newStory(sessionId, projectId,
                "T", "r", "a", "b", Priority.LOW, 1);

        assertThat(s.getDraftAcceptanceCriteria()).isEmpty();
    }

    // ── SuggestionCreatedEvent: registered once the aggregate is fully built ──

    @Nested
    @DisplayName("SuggestionCreatedEvent")
    class CreatedEvent {

        private final Suggestion.DraftCriterion penalty = new Suggestion.DraftCriterion(
                "Penalidad por cancelación tardía", "una cita reservada",
                "el paciente la cancela con menos de 24 horas de anticipación",
                "se le cobra una penalidad del 10 %");

        private SuggestionCreatedEvent onlyCreatedEvent(Suggestion s) {
            List<Object> events = AggregateEvents.of(s);
            assertThat(events).singleElement().isInstanceOf(SuggestionCreatedEvent.class);
            return (SuggestionCreatedEvent) events.getFirst();
        }

        @Test
        @DisplayName("NEW_STORY: the event carries the type, title and every draft criterion")
        void new_story_event_carries_criteria() {
            Suggestion s = Suggestion.newStory(sessionId, projectId,
                    "Reserva de cita médica", "paciente", "reservar una cita médica", "ser atendido a tiempo",
                    Priority.HIGH, 3, List.of(
                            new Suggestion.DraftCriterion("Reserva confirmada", "un horario libre",
                                    "el paciente lo reserva", "la cita queda confirmada"),
                            penalty));

            SuggestionCreatedEvent event = onlyCreatedEvent(s);

            assertThat(event.suggestionId()).isEqualTo(s.getId());
            assertThat(event.type()).isEqualTo(SuggestionType.NEW_STORY);
            assertThat(event.draftTitle()).isEqualTo("Reserva de cita médica");
            assertThat(event.targetStoryId()).isNull();
            assertThat(event.draftAcceptanceCriteria())
                    .isEqualTo(s.getDraftAcceptanceCriteria())
                    .extracting(Suggestion.DraftCriterion::scenario)
                    .containsExactly("Reserva confirmada", "Penalidad por cancelación tardía");
        }

        @Test
        @DisplayName("EDGE_CASE: the event says EDGE_CASE (not NEW_STORY) and carries its criterion, topic and target")
        void edge_case_event_carries_type_and_criterion() {
            UUID target = UUID.randomUUID();
            Suggestion s = Suggestion.edgeCase(sessionId, projectId,
                    "Penalidad por cancelación tardía", "paciente", "cancelar una cita médica",
                    "se respeten los horarios", Priority.HIGH, 2, "reserva de citas", target, penalty);

            SuggestionCreatedEvent event = onlyCreatedEvent(s);

            assertThat(event.type()).isEqualTo(SuggestionType.EDGE_CASE);
            assertThat(event.draftTitle()).isEqualTo("Penalidad por cancelación tardía");
            assertThat(event.relatedTopic()).isEqualTo("reserva de citas");
            assertThat(event.targetStoryId()).isEqualTo(target);
            assertThat(event.draftAcceptanceCriteria()).containsExactly(penalty);
        }

        @Test
        @DisplayName("EDGE_CASE without a criterion: the event still says EDGE_CASE, with no criteria")
        void edge_case_without_criterion() {
            Suggestion s = Suggestion.edgeCase(sessionId, projectId,
                    "Cuenta bloqueada", "usuario", "iniciar sesión", "acceder",
                    Priority.MEDIUM, 2, null, null, null);

            SuggestionCreatedEvent event = onlyCreatedEvent(s);

            assertThat(event.type()).isEqualTo(SuggestionType.EDGE_CASE);
            assertThat(event.targetStoryId()).isNull();
            assertThat(event.draftAcceptanceCriteria()).isEmpty();
        }

        @Test
        @DisplayName("UPDATE_STORY: the event carries the target and the criteria the update adds")
        void update_story_event_carries_target_and_criteria() {
            UUID target = UUID.randomUUID();
            Suggestion s = Suggestion.updateStory(sessionId, projectId,
                    "Reserva de cita médica", "paciente", "reservar una cita médica", "ser atendido a tiempo",
                    Priority.HIGH, 3, target, List.of(penalty));

            SuggestionCreatedEvent event = onlyCreatedEvent(s);

            assertThat(event.type()).isEqualTo(SuggestionType.UPDATE_STORY);
            assertThat(event.draftTitle()).isEqualTo("Reserva de cita médica");
            assertThat(event.targetStoryId()).isEqualTo(target);
            assertThat(event.draftAcceptanceCriteria()).containsExactly(penalty);
        }

        @Test
        @DisplayName("CLARIFYING_QUESTION: the event carries the question and no story payload")
        void clarifying_question_event_carries_question() {
            Suggestion s = Suggestion.clarifyingQuestion(sessionId, projectId,
                    "¿Quién aprueba la cancelación tardía?");

            SuggestionCreatedEvent event = onlyCreatedEvent(s);

            assertThat(event.type()).isEqualTo(SuggestionType.CLARIFYING_QUESTION);
            assertThat(event.question()).isEqualTo("¿Quién aprueba la cancelación tardía?");
            assertThat(event.draftTitle()).isNull();
            assertThat(event.draftAcceptanceCriteria()).isEmpty();
        }

        @Test
        @DisplayName("the event carries the sanitized criteria, the same ones REST and the database return")
        void event_carries_sanitized_criteria() {
            Suggestion s = newStoryWith(List.of(
                    new Suggestion.DraftCriterion("  ", "  tiene cuenta  ", "ingresa", "accede"),
                    new Suggestion.DraftCriterion("incompleta", "", "w", "t")));

            assertThat(onlyCreatedEvent(s).draftAcceptanceCriteria())
                    .containsExactly(new Suggestion.DraftCriterion(null, "tiene cuenta", "ingresa", "accede"));
        }
    }
}
