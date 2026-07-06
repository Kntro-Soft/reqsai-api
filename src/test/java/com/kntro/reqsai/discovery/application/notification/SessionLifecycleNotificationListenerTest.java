package com.kntro.reqsai.discovery.application.notification;

import com.kntro.reqsai.discovery.domain.event.DiscoverySessionCreatedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionRecordingPausedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionRecordingResumedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionRecordingStartedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionRecordingStoppedEvent;
import com.kntro.reqsai.discovery.domain.model.SessionStatus;
import com.kntro.reqsai.discovery.interfaces.notification.SessionEventType;
import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionLifecycleMessage;
import com.kntro.reqsai.shared.application.notification.RealtimeNotifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for the project-level lifecycle listener: every session lifecycle event must be
 * broadcast on the project topic ({@link ProjectTopics}) with a self-describing payload (status,
 * title, language, startedAt) so a viewer on the project page can render the session without an
 * extra fetch.
 *
 * @see SessionLifecycleNotificationListener
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Realtime: SessionLifecycleNotificationListener")
class SessionLifecycleNotificationListenerTest {

    @Mock
    private RealtimeNotifier notifier;
    @InjectMocks
    private SessionLifecycleNotificationListener listener;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final Instant startedAt = Instant.now();

    @Test
    @DisplayName("should broadcast SESSION_CREATED with the full session descriptor")
    void should_notify_session_created() {
        listener.onSessionCreated(
                DiscoverySessionCreatedEvent.of(sessionId, projectId, "Kickoff", "es-PE", startedAt));

        SessionLifecycleMessage msg = capture();
        assertThat(msg.type()).isEqualTo(SessionEventType.SESSION_CREATED);
        assertThat(msg.status()).isEqualTo(SessionStatus.DRAFT);
        assertThat(msg.title()).isEqualTo("Kickoff");
        assertThat(msg.language()).isEqualTo("es-PE");
        assertThat(msg.startedAt()).isEqualTo(startedAt);
    }

    @Test
    @DisplayName("should broadcast RECORDING_STARTED with status RECORDING")
    void should_notify_recording_started() {
        listener.onRecordingStarted(
                DiscoverySessionRecordingStartedEvent.of(sessionId, projectId, "Kickoff", "es-PE", startedAt));

        SessionLifecycleMessage msg = capture();
        assertThat(msg.type()).isEqualTo(SessionEventType.RECORDING_STARTED);
        assertThat(msg.status()).isEqualTo(SessionStatus.RECORDING);
        assertThat(msg.language()).isEqualTo("es-PE");
    }

    @Test
    @DisplayName("should broadcast RECORDING_PAUSED with status PAUSED")
    void should_notify_recording_paused() {
        listener.onRecordingPaused(
                DiscoverySessionRecordingPausedEvent.of(sessionId, projectId, "Kickoff", "es-PE", startedAt));

        SessionLifecycleMessage msg = capture();
        assertThat(msg.type()).isEqualTo(SessionEventType.RECORDING_PAUSED);
        assertThat(msg.status()).isEqualTo(SessionStatus.PAUSED);
    }

    @Test
    @DisplayName("should broadcast RECORDING_RESUMED with status RECORDING")
    void should_notify_recording_resumed() {
        listener.onRecordingResumed(
                DiscoverySessionRecordingResumedEvent.of(sessionId, projectId, "Kickoff", "es-PE", startedAt));

        SessionLifecycleMessage msg = capture();
        assertThat(msg.type()).isEqualTo(SessionEventType.RECORDING_RESUMED);
        assertThat(msg.status()).isEqualTo(SessionStatus.RECORDING);
    }

    @Test
    @DisplayName("should broadcast RECORDING_STOPPED with status STOPPED")
    void should_notify_recording_stopped() {
        listener.onRecordingStopped(
                DiscoverySessionRecordingStoppedEvent.of(sessionId, projectId, "Kickoff", "es-PE", startedAt));

        SessionLifecycleMessage msg = capture();
        assertThat(msg.type()).isEqualTo(SessionEventType.RECORDING_STOPPED);
        assertThat(msg.status()).isEqualTo(SessionStatus.STOPPED);
    }

    /** Verifies the broadcast went to the project topic and returns the message. */
    private SessionLifecycleMessage capture() {
        var captor = ArgumentCaptor.forClass(SessionLifecycleMessage.class);
        verify(notifier).broadcast(eq(ProjectTopics.sessionsOf(projectId)), captor.capture());
        SessionLifecycleMessage msg = captor.getValue();
        assertThat(msg.sessionId()).isEqualTo(sessionId);
        assertThat(msg.projectId()).isEqualTo(projectId);
        return msg;
    }
}
