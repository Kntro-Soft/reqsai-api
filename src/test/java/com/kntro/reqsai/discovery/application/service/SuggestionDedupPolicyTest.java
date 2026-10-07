package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.service.SuggestionDedupPolicy.Draft;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.SuggestionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SuggestionDedupPolicy}: which drafts are repeats, when a NEW draft may converge
 * into an update of an accepted story, and when an update changes nothing. No network, no embeddings —
 * the cosine is passed in, so each case states the similarity it assumes.
 */
@DisplayName("Application: SuggestionDedupPolicy")
class SuggestionDedupPolicyTest {

    private final SuggestionDedupPolicy policy = new SuggestionDedupPolicy(0.84);

    // The three requirements of the production meeting that lost the penalty rule.
    private static final Draft BOOKING = newStory("Reservar cita médica desde el portal web", "paciente",
            "reservar una cita médica desde el portal web eligiendo especialidad, médico y horario disponible",
            "pueda ser atendido en el horario que me convenga");
    private static final Draft PENALTY = newStory("Penalidad por cancelación tardía de cita", "paciente",
            "que se me cobre una penalidad del 10% si cancelo una cita médica con menos de 24 horas de anticipación",
            "se respeten los horarios reservados de los médicos");
    private static final Draft NOTIFICATION = newStory("Notificar al médico por correo sobre nuevas reservas",
            "médico", "recibir una notificación por correo cuando se reserve una cita conmigo",
            "pueda organizar mi agenda");

    private static Draft newStory(String title, String role, String action, String benefit) {
        return new Draft(SuggestionType.NEW_STORY, title, role, action, benefit, List.of());
    }

    private static Suggestion.DraftCriterion criterion(String given, String when, String then) {
        return new Suggestion.DraftCriterion(null, given, when, then);
    }

    @Nested
    @DisplayName("repeats (a draft is dropped only when it adds nothing)")
    class Repeats {

        @ParameterizedTest(name = "cosine {0}")
        @ValueSource(doubles = {0.86, 0.93, 0.99})
        @DisplayName("the cancellation penalty is never a repeat of the booking, however similar the embeddings")
        void penalty_is_not_a_repeat_of_booking(double cosine) {
            var verdict = policy.repeats(PENALTY, BOOKING, cosine, false);

            assertThat(verdict.duplicate()).isFalse();
            assertThat(verdict.reason()).contains("10").contains("24");
        }

        @Test
        @DisplayName("a penalty written with spelled-out numbers (as speech-to-text emits it) is not a repeat either")
        void spelled_out_penalty_is_not_a_repeat() {
            Draft cancelWithPenalty = newStory("Reservar cita médica desde el portal web", "paciente",
                    "reservar una cita médica desde el portal web y pagar el diez por ciento si cancelo con menos "
                            + "de veinticuatro horas", "pueda ser atendido en el horario que me convenga");

            var verdict = policy.repeats(cancelWithPenalty, BOOKING, 0.99, false);

            assertThat(verdict.duplicate()).isFalse();
            assertThat(verdict.reason()).contains("diez").contains("veinticuatro");
        }

        @Test
        @DisplayName("the doctor's e-mail notification is not a repeat of the booking")
        void notification_is_not_a_repeat_of_booking() {
            assertThat(policy.repeats(NOTIFICATION, BOOKING, 0.95, false).duplicate()).isFalse();
        }

        @Test
        @DisplayName("cancelling a booked appointment is not a repeat of booking it (same actor, same nouns)")
        void cancellation_is_not_a_repeat_of_booking() {
            Draft cancel = newStory("Cancelar cita médica", "paciente",
                    "cancelar una cita médica reservada desde el portal web", "liberar el horario si no puedo asistir");

            assertThat(policy.repeats(cancel, BOOKING, 0.85, false).duplicate()).isFalse();
        }

        @Test
        @DisplayName("a draft that repeats the booking and adds one clause is kept (embeddings cannot see the clause)")
        void added_clause_is_not_a_repeat() {
            Draft bookAndPay = newStory("Reservar cita médica desde el portal web", "paciente",
                    "reservar una cita médica desde el portal web eligiendo especialidad, médico y horario "
                            + "disponible, y pagarla en línea con tarjeta",
                    "pueda ser atendido en el horario que me convenga");

            var verdict = policy.repeats(bookAndPay, BOOKING, 0.995, false);

            assertThat(verdict.duplicate()).isFalse();
            assertThat(verdict.reason()).contains("pagar");
        }

        @Test
        @DisplayName("a true paraphrase of the booking (same words, other order and inflection) is a repeat")
        void restatement_of_booking_is_a_repeat() {
            Draft restatement = newStory("Reservar citas médicas", "paciente",
                    "reservar en el portal web una cita médica, eligiendo médico, especialidad y horario",
                    "me atiendan en un horario que me convenga");

            var verdict = policy.repeats(restatement, BOOKING, 0.97, false);

            assertThat(verdict.duplicate()).isTrue();
        }

        @Test
        @DisplayName("the same title with nothing new is a repeat even without embeddings")
        void same_title_without_embeddings_is_a_repeat() {
            Draft again = newStory("Reservar cita médica desde el portal web", "paciente",
                    "reservar una cita médica en el portal web", "ser atendido");

            assertThat(policy.repeats(again, BOOKING, null, false).duplicate()).isTrue();
        }

        @Test
        @DisplayName("a draft the model linked to an earlier one is a repeat when it adds nothing (no cosine needed)")
        void linked_restatement_is_a_repeat() {
            Draft linked = new Draft(SuggestionType.UPDATE_STORY, "Reserva de cita médica", "paciente",
                    "reservar una cita médica en el portal web", "ser atendido", List.of());

            assertThat(policy.repeats(linked, BOOKING, null, true).duplicate()).isTrue();
        }

        @Test
        @DisplayName("an EDGE_CASE linked to an earlier draft that brings a new criterion is not a repeat")
        void linked_edge_case_with_new_criterion_is_not_a_repeat() {
            Draft edge = new Draft(SuggestionType.EDGE_CASE, "Reservar cita médica", "paciente",
                    "reservar una cita médica", "ser atendido",
                    List.of(criterion("una cita reservada", "el paciente la cancela tarde",
                            "se le cobra una penalidad")));

            var verdict = policy.repeats(edge, BOOKING, null, true);

            assertThat(verdict.duplicate()).isFalse();
            assertThat(verdict.reason()).contains("criteria");
        }

        @Test
        @DisplayName("a draft that adds no word but is unrelated by embedding and title is not a repeat")
        void unrelated_is_not_a_repeat() {
            Draft subset = newStory("Especialidad y horario", "paciente", "especialidad y horario disponible",
                    "ser atendido");

            assertThat(policy.repeats(subset, BOOKING, 0.60, false).duplicate()).isFalse();
            assertThat(policy.repeats(subset, BOOKING, null, false).duplicate()).isFalse();
        }
    }

    @Nested
    @DisplayName("sameRequirementAs (a NEW draft converging into an update of an accepted story)")
    class SameRequirement {

        @Test
        @DisplayName("the penalty does not converge into the booking story, even above the similarity bar")
        void penalty_does_not_converge_into_booking() {
            assertThat(policy.sameRequirementAs(PENALTY, BOOKING, 0.90).duplicate()).isFalse();
            assertThat(policy.sameRequirementAs(NOTIFICATION, BOOKING, 0.90).duplicate()).isFalse();
        }

        @Test
        @DisplayName("a reworded booking (same actor and action) converges into the booking story")
        void reworded_booking_converges() {
            Draft reworded = newStory("Reservar una cita en línea", "paciente registrado",
                    "reservar una cita médica en la web seleccionando especialidad, médico y horario",
                    "no tener que llamar a la clínica");

            assertThat(policy.sameRequirementAs(reworded, BOOKING, 0.88).duplicate()).isTrue();
        }

        @Test
        @DisplayName("a near-identical title converges; anything below the similarity bar never does")
        void title_converges_but_bar_still_applies() {
            Draft sameTitle = newStory("Reservar cita médica en el portal web", "usuario", "agendar", "x");

            assertThat(policy.sameRequirementAs(sameTitle, BOOKING, 0.85).duplicate()).isTrue();
            assertThat(policy.sameRequirementAs(sameTitle, BOOKING, 0.80).duplicate()).isFalse();
        }
    }

    @Nested
    @DisplayName("no-op update detection")
    class NoOpUpdate {

        @Test
        @DisplayName("the same narrative modulo case, accents, punctuation and filler words is unchanged")
        void same_narrative_ignores_cosmetics() {
            Draft proposal = newStory("reservar cita medica desde el portal web.", "Paciente",
                    "Reservar una cita médica desde el portal web, eligiendo especialidad, médico y el horario "
                            + "disponible", "pueda ser atendido en el horario que me convenga");

            assertThat(policy.sameNarrative(proposal, BOOKING)).isTrue();
        }

        @Test
        @DisplayName("one new content word in any field is a change")
        void one_new_word_is_a_change() {
            Draft proposal = newStory("Reservar cita médica desde el portal web", "paciente",
                    "reservar una cita médica desde el portal web o la app eligiendo especialidad, médico y "
                            + "horario disponible", "pueda ser atendido en el horario que me convenga");

            assertThat(policy.sameNarrative(proposal, BOOKING)).isFalse();
        }

        @Test
        @DisplayName("criteriaMissingFrom keeps only the criteria the story does not already state")
        void criteria_missing_from_filters_equivalents() {
            var existing = List.of(criterion("una cita disponible", "el paciente la reserva", "queda confirmada"));
            var proposed = List.of(
                    criterion("Una cita disponible", "el paciente la reserva.", "Queda confirmada"),
                    criterion("una cita reservada", "el paciente la cancela con menos de 24 horas",
                            "se le cobra una penalidad del 10 %"));

            var missing = policy.criteriaMissingFrom(proposed, existing);

            assertThat(missing).singleElement()
                    .satisfies(c -> assertThat(c.then()).contains("penalidad"));
        }
    }

    @Nested
    @DisplayName("lexical helpers")
    class Lexical {

        @Test
        @DisplayName("numbers: standalone digits and number words, not digits inside a code")
        void numbers_are_amounts_not_codes() {
            assertThat(SuggestionDedupPolicy.numbers("una penalidad del 10% con 24 horas"))
                    .containsExactlyInAnyOrder("10", "24");
            assertThat(SuggestionDedupPolicy.numbers("diez por ciento con veinticuatro horas"))
                    .containsExactlyInAnyOrder("diez", "ciento", "veinticuatro");
            assertThat(SuggestionDedupPolicy.numbers("login con 2FA y OAuth2")).isEmpty();
        }

        @Test
        @DisplayName("tokens: accent-folded, singular, stemmed, without function words")
        void tokens_fold_and_stem() {
            assertThat(SuggestionDedupPolicy.tokens("Reservar las citas médicas"))
                    .containsExactly("reser", "cita", "medic");
            assertThat(SuggestionDedupPolicy.tokens("reservada la cita médica"))
                    .containsExactly("reser", "cita", "medic");
        }
    }
}
