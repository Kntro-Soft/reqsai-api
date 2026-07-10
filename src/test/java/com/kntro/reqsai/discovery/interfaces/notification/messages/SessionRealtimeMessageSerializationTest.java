package com.kntro.reqsai.discovery.interfaces.notification.messages;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.SessionStatus;
import com.kntro.reqsai.discovery.domain.model.SuggestionStatus;
import com.kntro.reqsai.discovery.domain.model.SuggestionType;
import com.kntro.reqsai.discovery.interfaces.notification.SessionEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the wire contract every {@link SessionRealtimeMessage} implementation must honor: the
 * serialized JSON must carry a {@code "type"} field matching {@link SessionRealtimeMessage#type()}.
 * <p>
 * This is not redundant with the per-message unit tests: those inspect the Java object directly and
 * would pass even if {@code type} were silently dropped from the JSON. That exact bug shipped once —
 * {@code SessionPresenceMessage} overrode {@code type()} without a canonical record component or a
 * {@code @JsonProperty("type")} annotation, so Jackson's record serializer (which only emits canonical
 * components) omitted it entirely. The client's {@code message.type === 'PRESENCE_STATE'} switch then
 * silently never matched, even though the message otherwise arrived correctly. Only a real
 * {@link ObjectMapper} pass over the actual JSON output can catch this class of bug.
 */
class SessionRealtimeMessageSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @ParameterizedTest(name = "{0} serializes type={1}")
    @MethodSource("messages")
    @DisplayName("every SessionRealtimeMessage serializes a matching \"type\" field")
    void serializesTypeField(SessionRealtimeMessage message, SessionEventType expectedType) throws Exception {
        String json = mapper.writeValueAsString(message);

        assertThat(json).contains("\"type\":\"" + expectedType.name() + "\"");
        var node = mapper.readTree(json);
        assertThat(node.get("type").asText()).isEqualTo(expectedType.name());
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> messages() {
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(
                        SessionPresenceMessage.of(sessionId, List.of(), now), SessionEventType.PRESENCE_STATE),
                org.junit.jupiter.params.provider.Arguments.of(
                        SessionStatusChangedMessage.of(sessionId, SessionEventType.RECORDING_STARTED, now),
                        SessionEventType.RECORDING_STARTED),
                org.junit.jupiter.params.provider.Arguments.of(
                        new SessionProcessingFailedMessage(sessionId, "boom", now), SessionEventType.FAILED),
                org.junit.jupiter.params.provider.Arguments.of(
                        new SessionStoryGeneratedMessage(sessionId, UUID.randomUUID(), "Title", "role", "action",
                                "benefit", Priority.MEDIUM, null, now),
                        SessionEventType.STORY_GENERATED),
                org.junit.jupiter.params.provider.Arguments.of(
                        new SessionTranscriptSegmentMessage(sessionId, 0, null, "text", 0L, 100L, true, now),
                        SessionEventType.TRANSCRIPT_SEGMENT),
                org.junit.jupiter.params.provider.Arguments.of(
                        new SessionSuggestionMessage(sessionId, UUID.randomUUID(), SessionEventType.SUGGESTION_GENERATED,
                                SuggestionType.NEW_STORY, SuggestionStatus.PENDING, null, null, null, null, null, null,
                                null, null, null, List.of(), null, now),
                        SessionEventType.SUGGESTION_GENERATED),
                org.junit.jupiter.params.provider.Arguments.of(
                        new SessionLifecycleMessage(sessionId, UUID.randomUUID(), SessionEventType.SESSION_CREATED,
                                SessionStatus.DRAFT, "Title", "es-PE", null, now),
                        SessionEventType.SESSION_CREATED)
        );
    }

    @Test
    @DisplayName("regression: SessionPresenceMessage.type() carries the required @JsonProperty (else Jackson drops it)")
    void presenceMessageTypeAccessorIsAnnotated() throws Exception {
        var method = SessionPresenceMessage.class.getMethod("type");
        assertThat(method.isAnnotationPresent(com.fasterxml.jackson.annotation.JsonProperty.class))
                .as("type() must be @JsonProperty(\"type\") annotated since it is not a canonical record component")
                .isTrue();
    }
}
