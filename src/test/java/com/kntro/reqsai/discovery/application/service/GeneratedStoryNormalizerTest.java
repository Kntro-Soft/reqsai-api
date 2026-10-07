package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.GenerationResult;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.SuggestionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GeneratedStoryNormalizer}. The web prints "Como {role}, quiero {action}, para
 * {benefit}." and "Dado / Cuando / Entonces {step}", so a generated field must not repeat those keywords.
 * The Spanish cases are the generated texts seen in production.
 */
@Tag("unit")
@DisplayName("Application: GeneratedStoryNormalizer")
class GeneratedStoryNormalizerTest {

    @Nested
    @DisplayName("user-story narrative")
    class Narrative {

        @ParameterizedTest(name = "role \"{0}\" -> \"{1}\"")
        @CsvSource(delimiter = '|', textBlock = """
                Como paciente                                | paciente
                como un paciente                             | un paciente
                Como: Paciente que busca un horario          | Paciente que busca un horario
                Paciente que busca un horario disponible.    | Paciente que busca un horario disponible
                El paciente                                  | el paciente
                As a patient                                 | a patient
                As an administrator                          | an administrator
                The store manager                            | the store manager
                Asistente administrativo                     | Asistente administrativo
                """)
        void role(String generated, String expected) {
            assertThat(GeneratedStoryNormalizer.role(generated)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "action \"{0}\" -> \"{1}\"")
        @CsvSource(delimiter = '|', textBlock = """
                Quiero reservar una cita desde la web o desde el celular.  | reservar una cita desde la web o desde el celular
                quiero ver mis citas                                       | ver mis citas
                Yo quiero ver mis citas;                                   | ver mis citas
                Quiero que el sistema me avise                             | que el sistema me avise
                Quiero pagar con Yape                                      | pagar con Yape
                reservar un horario disponible.                            | reservar un horario disponible
                I want to book an appointment online.                      | to book an appointment online
                I want the system to remind me                             | the system to remind me
                """)
        void action(String generated, String expected) {
            assertThat(GeneratedStoryNormalizer.action(generated)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "benefit \"{0}\" -> \"{1}\"")
        @CsvSource(delimiter = '|', textBlock = """
                Para evitar largas colas y llamadas sin contestar.  | evitar largas colas y llamadas sin contestar
                para que el paciente no pierda su turno             | que el paciente no pierda su turno
                Para poder pagar en línea                           | poder pagar en línea
                So that I avoid long queues.                        | I avoid long queues
                so that the clinic keeps its schedule,              | the clinic keeps its schedule
                """)
        void benefit(String generated, String expected) {
            assertThat(GeneratedStoryNormalizer.benefit(generated)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("Gherkin steps")
    class Steps {

        @ParameterizedTest(name = "given \"{0}\" -> \"{1}\"")
        @CsvSource(delimiter = '|', textBlock = """
                Dado que un paciente está reservando una cita en línea  | que un paciente está reservando una cita en línea
                Dados los planes de seguro configurados                 | los planes de seguro configurados
                Dada una cita reservada                                 | una cita reservada
                Dadas dos reservas para el mismo horario                | dos reservas para el mismo horario
                DADO un horario libre                                   | un horario libre
                Dado: Un paciente tiene una cita                        | un paciente tiene una cita
                Un paciente tiene una cita reservada                    | un paciente tiene una cita reservada
                Given a booked appointment.                             | a booked appointment
                Given that the user is logged in                        | that the user is logged in
                Given The user has a pending refund                     | the user has a pending refund
                Dadores de sangre registrados                           | Dadores de sangre registrados
                """)
        void given(String generated, String expected) {
            assertThat(GeneratedStoryNormalizer.given(generated)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "when \"{0}\" -> \"{1}\"")
        @CsvSource(delimiter = '|', textBlock = """
                Cuando el primero confirma su reserva   | el primero confirma su reserva
                cuando la devolución se procesa         | la devolución se procesa
                Cuando, Un paciente cancela             | un paciente cancela
                When the patient cancels the booking    | the patient cancels the booking
                Y cuando el pago falla                  | el pago falla
                Y/o el administrador la rechaza         | Y/o el administrador la rechaza
                """)
        void when(String generated, String expected) {
            assertThat(GeneratedStoryNormalizer.when(generated)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "then \"{0}\" -> \"{1}\"")
        @CsvSource(delimiter = '|', textBlock = """
                Entonces se queda con el horario.                 | se queda con el horario
                entonces el reembolso se acredita en 5 días       | el reembolso se acredita en 5 días
                Entonces Se envía un correo de confirmación.      | se envía un correo de confirmación
                Y se notifica al médico                           | se notifica al médico
                Then the refund is processed.                     | the refund is processed
                And then the refund is processed                  | the refund is processed
                """)
        void then(String generated, String expected) {
            assertThat(GeneratedStoryNormalizer.then(generated)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("names and acronyms")
    class ProperNouns {

        @Test
        @DisplayName("acronyms, names and brands keep their case after a removed keyword")
        void keeps_proper_nouns() {
            assertThat(GeneratedStoryNormalizer.given("Dado DNI inválido ingresado")).isEqualTo("DNI inválido ingresado");
            assertThat(GeneratedStoryNormalizer.then("Entonces SUNAT recibe la factura")).isEqualTo("SUNAT recibe la factura");
            assertThat(GeneratedStoryNormalizer.when("Cuando María cancela la cita")).isEqualTo("María cancela la cita");
            assertThat(GeneratedStoryNormalizer.action("Quiero Yape como medio de pago")).isEqualTo("Yape como medio de pago");
            assertThat(GeneratedStoryNormalizer.then("Then I receive an email")).isEqualTo("I receive an email");
        }

        @Test
        @DisplayName("a function word that opens a name ('La Molina', 'El Salvador') or is in upper case is kept")
        void keeps_function_words_that_open_a_name() {
            assertThat(GeneratedStoryNormalizer.given("Dado La Molina fuera de la zona de reparto"))
                    .isEqualTo("La Molina fuera de la zona de reparto");
            assertThat(GeneratedStoryNormalizer.given("El Salvador como país de envío"))
                    .isEqualTo("El Salvador como país de envío");
            assertThat(GeneratedStoryNormalizer.given("EL sistema está caído")).isEqualTo("EL sistema está caído");
            assertThat(GeneratedStoryNormalizer.given("Un DNI inválido")).isEqualTo("un DNI inválido");
        }
    }

    @Nested
    @DisplayName("never empties a field")
    class EmptyGuard {

        @Test
        @DisplayName("keeps the original text when removing the keyword or the period would leave nothing")
        void keeps_original_when_nothing_is_left() {
            assertThat(GeneratedStoryNormalizer.given("Dado")).isEqualTo("Dado");
            assertThat(GeneratedStoryNormalizer.then("Entonces.")).isEqualTo("Entonces.");
            assertThat(GeneratedStoryNormalizer.when("And When")).isEqualTo("And When");
            assertThat(GeneratedStoryNormalizer.action("Quiero")).isEqualTo("Quiero");
            assertThat(GeneratedStoryNormalizer.role("As")).isEqualTo("As");
            assertThat(GeneratedStoryNormalizer.benefit("...")).isEqualTo("...");
        }

        @Test
        @DisplayName("null and blank values are returned unchanged")
        void null_and_blank_are_unchanged() {
            assertThat(GeneratedStoryNormalizer.role(null)).isNull();
            assertThat(GeneratedStoryNormalizer.then(null)).isNull();
            assertThat(GeneratedStoryNormalizer.action("  ")).isEqualTo("  ");
        }
    }

    @Nested
    @DisplayName("whole generated story")
    class WholeStory {

        @Test
        @DisplayName("normalizes the narrative and every step; title, scenario and metadata are kept")
        void normalizes_narrative_and_steps_only() {
            UUID target = UUID.randomUUID();
            var gen = new GenerationResult.GeneratedStory(SuggestionType.EDGE_CASE,
                    "Quiero reservar citas.", "Como paciente", "Quiero reservar una cita.",
                    "Para evitar largas colas.", Priority.HIGH, 3,
                    List.of(new GenerationResult.GeneratedCriterion("Dado un horario ocupado",
                            "Dado que un paciente está reservando una cita en línea",
                            "Cuando el primero confirma su reserva",
                            "Entonces se queda con el horario.")),
                    "reservas", target);

            var normalized = GeneratedStoryNormalizer.normalize(gen);

            assertThat(normalized.title()).isEqualTo("Quiero reservar citas.");
            assertThat(normalized.role()).isEqualTo("paciente");
            assertThat(normalized.action()).isEqualTo("reservar una cita");
            assertThat(normalized.benefit()).isEqualTo("evitar largas colas");
            assertThat(normalized.acceptanceCriteria()).containsExactly(new GenerationResult.GeneratedCriterion(
                    "Dado un horario ocupado", "que un paciente está reservando una cita en línea",
                    "el primero confirma su reserva", "se queda con el horario"));
            assertThat(normalized.type()).isEqualTo(SuggestionType.EDGE_CASE);
            assertThat(normalized.priority()).isEqualTo(Priority.HIGH);
            assertThat(normalized.storyPoints()).isEqualTo(3);
            assertThat(normalized.relatedTopic()).isEqualTo("reservas");
            assertThat(normalized.targetStoryId()).isEqualTo(target);
        }

        @Test
        @DisplayName("a missing criteria list or criterion stays missing")
        void keeps_missing_criteria() {
            var noCriteria = new GenerationResult.GeneratedStory(SuggestionType.NEW_STORY, "T", "paciente",
                    "reservar", "evitar colas", Priority.LOW, null, null, null, null);
            assertThat(GeneratedStoryNormalizer.normalize(noCriteria).acceptanceCriteria()).isNull();

            List<GenerationResult.GeneratedCriterion> withNull = new ArrayList<>(Arrays.asList(
                    null, new GenerationResult.GeneratedCriterion(null, "Given a slot", "When booked", "Then held.")));
            var gen = new GenerationResult.GeneratedStory(SuggestionType.NEW_STORY, "T", "a patient",
                    "to book", "I save time", Priority.LOW, null, withNull, null, null);
            assertThat(GeneratedStoryNormalizer.normalize(gen).acceptanceCriteria()).containsExactly(
                    null, new GenerationResult.GeneratedCriterion(null, "a slot", "booked", "held"));
        }
    }
}
