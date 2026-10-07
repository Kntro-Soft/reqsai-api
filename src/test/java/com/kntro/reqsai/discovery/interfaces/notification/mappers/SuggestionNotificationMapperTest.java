package com.kntro.reqsai.discovery.interfaces.notification.mappers;

import com.kntro.reqsai.discovery.domain.event.SuggestionCreatedEvent;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.SuggestionStatus;
import com.kntro.reqsai.discovery.domain.model.SuggestionType;
import com.kntro.reqsai.discovery.interfaces.notification.SessionEventType;
import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionSuggestionMessage;
import com.kntro.reqsai.testsupport.AggregateEvents;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the suggestion-event → WebSocket-message mapping, fed with the event a real
 * {@link Suggestion} factory registers: the live {@code SUGGESTION_GENERATED} card must show the same
 * type and criteria that REST and the database return.
 *
 * @see SuggestionNotificationMapper
 */
@DisplayName("Realtime: SuggestionNotificationMapper")
class SuggestionNotificationMapperTest {

    private final UUID sessionId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    private static SuggestionCreatedEvent createdEventOf(Suggestion s) {
        return AggregateEvents.of(s).stream()
                .filter(SuggestionCreatedEvent.class::isInstance)
                .map(SuggestionCreatedEvent.class::cast)
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("should send an EDGE_CASE as EDGE_CASE with its criterion and target")
    void should_map_edge_case_with_its_criterion() {
        UUID target = UUID.randomUUID();
        Suggestion.DraftCriterion criterion = new Suggestion.DraftCriterion("Penalidad por cancelación tardía",
                "una cita reservada", "el paciente la cancela con menos de 24 horas",
                "se le cobra una penalidad del 10 %");
        Suggestion edge = Suggestion.edgeCase(sessionId, projectId,
                "Penalidad por cancelación tardía", "paciente", "cancelar una cita médica",
                "se respeten los horarios", Priority.HIGH, 2, "reserva de citas", target, criterion);

        SessionSuggestionMessage msg = SuggestionNotificationMapper.toGeneratedMessage(createdEventOf(edge));

        assertThat(msg.type()).isEqualTo(SessionEventType.SUGGESTION_GENERATED);
        assertThat(msg.sessionId()).isEqualTo(sessionId);
        assertThat(msg.suggestionId()).isEqualTo(edge.getId());
        assertThat(msg.suggestionType()).isEqualTo(SuggestionType.EDGE_CASE);
        assertThat(msg.status()).isEqualTo(SuggestionStatus.PENDING);
        assertThat(msg.draftTitle()).isEqualTo("Penalidad por cancelación tardía");
        assertThat(msg.targetStoryId()).isEqualTo(target);
        assertThat(msg.relatedTopic()).isEqualTo("reserva de citas");
        assertThat(msg.draftAcceptanceCriteria()).containsExactly(criterion);
        assertThat(msg.draftAcceptanceCriteria()).isEqualTo(edge.getDraftAcceptanceCriteria());
    }

    @Test
    @DisplayName("should send a NEW_STORY with every draft criterion")
    void should_map_new_story_with_its_criteria() {
        Suggestion story = Suggestion.newStory(sessionId, projectId,
                "Notificación al médico al reservar una cita", "médico",
                "recibir un correo cuando un paciente reserva una cita conmigo", "organizar mi agenda",
                Priority.MEDIUM, 2, List.of(
                        new Suggestion.DraftCriterion("Correo al médico", "un paciente reserva una cita",
                                "la reserva se confirma", "el médico recibe un correo con la fecha y la hora"),
                        new Suggestion.DraftCriterion(null, "una cita reservada", "el paciente la cancela",
                                "el médico recibe un correo de cancelación")));

        SessionSuggestionMessage msg = SuggestionNotificationMapper.toGeneratedMessage(createdEventOf(story));

        assertThat(msg.suggestionType()).isEqualTo(SuggestionType.NEW_STORY);
        assertThat(msg.draftTitle()).isEqualTo("Notificación al médico al reservar una cita");
        assertThat(msg.draftAcceptanceCriteria()).hasSize(2).isEqualTo(story.getDraftAcceptanceCriteria());
    }
}
