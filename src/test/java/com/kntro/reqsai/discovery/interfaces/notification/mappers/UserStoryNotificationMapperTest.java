package com.kntro.reqsai.discovery.interfaces.notification.mappers;

import com.kntro.reqsai.discovery.domain.event.UserStoryCreatedEvent;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.interfaces.notification.SessionEventType;
import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionStoryGeneratedMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the story-event → WebSocket-message mapping: every story field must be forwarded so
 * the client can render the story without a follow-up GET, and a manual story (null sessionId) must
 * be rejected because it has no session topic to route to.
 *
 * @see UserStoryNotificationMapper
 */
@DisplayName("Realtime: UserStoryNotificationMapper")
class UserStoryNotificationMapperTest {

    @Test
    @DisplayName("should map a session-scoped story event, forwarding all fields")
    void should_map_story_event() {
        // Arrange
        UUID storyId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        var event = UserStoryCreatedEvent.of(storyId, sessionId, UUID.randomUUID(),
                "Login con Google", "usuario", "iniciar sesión con Google", "no recordar contraseñas",
                Priority.HIGH, 5);

        // Act
        SessionStoryGeneratedMessage msg = UserStoryNotificationMapper.toMessage(event);

        // Assert
        assertThat(msg.sessionId()).isEqualTo(sessionId);
        assertThat(msg.storyId()).isEqualTo(storyId);
        assertThat(msg.type()).isEqualTo(SessionEventType.STORY_GENERATED);
        assertThat(msg.title()).isEqualTo("Login con Google");
        assertThat(msg.role()).isEqualTo("usuario");
        assertThat(msg.action()).isEqualTo("iniciar sesión con Google");
        assertThat(msg.benefit()).isEqualTo("no recordar contraseñas");
        assertThat(msg.priority()).isEqualTo(Priority.HIGH);
        assertThat(msg.storyPoints()).isEqualTo(5);
        assertThat(msg.occurredAt()).isEqualTo(event.occurredAt());
    }

    @Test
    @DisplayName("should preserve a null storyPoints (estimation not provided)")
    void should_allow_null_story_points() {
        // Arrange
        var event = UserStoryCreatedEvent.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "title", "role", "action", "benefit", Priority.LOW, null);

        // Act
        SessionStoryGeneratedMessage msg = UserStoryNotificationMapper.toMessage(event);

        // Assert
        assertThat(msg.storyPoints()).isNull();
    }

    @Test
    @DisplayName("should reject a manual story event (null sessionId)")
    void should_reject_manual_story() {
        // Arrange
        var manual = UserStoryCreatedEvent.of(UUID.randomUUID(), null, UUID.randomUUID(),
                "title", "role", "action", "benefit", Priority.MEDIUM, null);

        // Act & Assert
        assertThatThrownBy(() -> UserStoryNotificationMapper.toMessage(manual))
                .isInstanceOf(NullPointerException.class);
    }
}
