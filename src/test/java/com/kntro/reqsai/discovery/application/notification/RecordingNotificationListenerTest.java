package com.kntro.reqsai.discovery.application.notification;

import com.kntro.reqsai.discovery.domain.event.DiscoverySessionRecordingPausedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionRecordingResumedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionRecordingStartedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionRecordingStoppedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionResetEvent;
import com.kntro.reqsai.discovery.interfaces.notification.SessionEventType;
import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionRealtimeMessage;
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

/**
 * Unit tests for the recording-lifecycle listener: each of the five recording events must broadcast
 * to the session topic ({@link SessionTopics}) with the matching {@link SessionEventType}.
 *
 * @see RecordingNotificationListener
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Realtime: RecordingNotificationListener")
class RecordingNotificationListenerTest {

    @Mock
    private RealtimeNotifier notifier;
    @InjectMocks
    private RecordingNotificationListener listener;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    @Test
    @DisplayName("should broadcast RECORDING_STARTED")
    void should_notify_recording_started() {
        // Act
        listener.onRecordingStarted(DiscoverySessionRecordingStartedEvent.of(sessionId, projectId));

        // Assert
        assertThat(captureType()).isEqualTo(SessionEventType.RECORDING_STARTED);
    }

    @Test
    @DisplayName("should broadcast RECORDING_PAUSED")
    void should_notify_recording_paused() {
        // Act
        listener.onRecordingPaused(DiscoverySessionRecordingPausedEvent.of(sessionId, projectId));

        // Assert
        assertThat(captureType()).isEqualTo(SessionEventType.RECORDING_PAUSED);
    }

    @Test
    @DisplayName("should broadcast RECORDING_RESUMED")
    void should_notify_recording_resumed() {
        // Act
        listener.onRecordingResumed(DiscoverySessionRecordingResumedEvent.of(sessionId, projectId));

        // Assert
        assertThat(captureType()).isEqualTo(SessionEventType.RECORDING_RESUMED);
    }

    @Test
    @DisplayName("should broadcast RECORDING_STOPPED")
    void should_notify_recording_stopped() {
        // Act
        listener.onRecordingStopped(DiscoverySessionRecordingStoppedEvent.of(sessionId, projectId));

        // Assert
        assertThat(captureType()).isEqualTo(SessionEventType.RECORDING_STOPPED);
    }

    @Test
    @DisplayName("should broadcast SESSION_RESET")
    void should_notify_session_reset() {
        // Act
        listener.onSessionReset(DiscoverySessionResetEvent.of(sessionId, projectId));

        // Assert
        assertThat(captureType()).isEqualTo(SessionEventType.SESSION_RESET);
    }

    /** Verifies the broadcast went to the canonical session topic and returns the message discriminator. */
    private SessionEventType captureType() {
        var captor = ArgumentCaptor.forClass(SessionRealtimeMessage.class);
        verify(notifier).broadcast(eq(SessionTopics.of(sessionId)), captor.capture());
        SessionRealtimeMessage msg = captor.getValue();
        assertThat(msg.sessionId()).isEqualTo(sessionId);
        return msg.type();
    }
}
