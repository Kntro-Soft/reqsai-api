package com.kntro.reqsai.discovery.application.notification;

import com.kntro.reqsai.discovery.domain.event.UserStoryCreatedEvent;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.interfaces.notification.SessionEventType;
import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionStoryGeneratedMessage;
import com.kntro.reqsai.shared.application.notification.RealtimeNotifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for the story listener: a session-scoped generated story is pushed to the session topic
 * with all fields; a manually created story (null sessionId) is ignored — there is no live session
 * page to update.
 *
 * @see StoryNotificationListener
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Realtime: StoryNotificationListener")
class StoryNotificationListenerTest {

    @Mock
    private RealtimeNotifier notifier;
    @InjectMocks
    private StoryNotificationListener listener;

    private final UUID projectId = UUID.randomUUID();

    @Test
    @DisplayName("should broadcast STORY_GENERATED with the story fields for a session-scoped story")
    void should_notify_story_generated() {
        // Arrange
        UUID storyId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        var event = UserStoryCreatedEvent.of(storyId, sessionId, projectId,
                "Login con Google", "usuario", "iniciar sesión con Google", "no recordar contraseñas",
                Priority.HIGH, 5);

        // Act
        listener.onStoryGenerated(event);

        // Assert
        var captor = ArgumentCaptor.forClass(SessionStoryGeneratedMessage.class);
        verify(notifier).broadcast(eq(SessionTopics.of(sessionId)), captor.capture());
        SessionStoryGeneratedMessage msg = captor.getValue();
        assertThat(msg.sessionId()).isEqualTo(sessionId);
        assertThat(msg.storyId()).isEqualTo(storyId);
        assertThat(msg.type()).isEqualTo(SessionEventType.STORY_GENERATED);
        assertThat(msg.title()).isEqualTo("Login con Google");
        assertThat(msg.priority()).isEqualTo(Priority.HIGH);
    }

    @Test
    @DisplayName("should ignore a manually created story (null sessionId)")
    void should_ignore_manual_story() {
        // Arrange
        var manual = UserStoryCreatedEvent.of(UUID.randomUUID(), null, projectId,
                "title", "role", "action", "benefit", Priority.LOW, null);

        // Act
        listener.onStoryGenerated(manual);

        // Assert
        verifyNoInteractions(notifier);
    }
}
