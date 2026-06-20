package com.kntro.reqsai.discovery.application.notification;

import com.kntro.reqsai.discovery.domain.event.DiscoverySessionProcessingCompletedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionProcessingFailedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionProcessingStartedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionTranscriptUploadedEvent;
import com.kntro.reqsai.discovery.interfaces.notification.SessionEventType;
import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionProcessingFailedMessage;
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
 * Unit tests for the AI-processing listener: the four processing events must broadcast to the session
 * topic with the matching type; the failed event must carry the failure reason.
 *
 * @see ProcessingNotificationListener
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Realtime: ProcessingNotificationListener")
class ProcessingNotificationListenerTest {

    @Mock
    private RealtimeNotifier notifier;
    @InjectMocks
    private ProcessingNotificationListener listener;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    @Test
    @DisplayName("should broadcast TRANSCRIPT_UPLOADED")
    void should_notify_transcript_uploaded() {
        // Act
        listener.onTranscriptUploaded(DiscoverySessionTranscriptUploadedEvent.of(sessionId, projectId));

        // Assert
        assertThat(captureMessage().type()).isEqualTo(SessionEventType.TRANSCRIPT_UPLOADED);
    }

    @Test
    @DisplayName("should broadcast PROCESSING")
    void should_notify_processing_started() {
        // Act
        listener.onProcessingStarted(DiscoverySessionProcessingStartedEvent.of(sessionId, projectId));

        // Assert
        assertThat(captureMessage().type()).isEqualTo(SessionEventType.PROCESSING);
    }

    @Test
    @DisplayName("should broadcast COMPLETED")
    void should_notify_processing_completed() {
        // Act
        listener.onProcessingCompleted(DiscoverySessionProcessingCompletedEvent.of(sessionId, projectId));

        // Assert
        assertThat(captureMessage().type()).isEqualTo(SessionEventType.COMPLETED);
    }

    @Test
    @DisplayName("should broadcast FAILED carrying the reason")
    void should_notify_processing_failed() {
        // Act
        listener.onProcessingFailed(DiscoverySessionProcessingFailedEvent.of(sessionId, projectId, "Quota exceeded"));

        // Assert
        SessionRealtimeMessage msg = captureMessage();
        assertThat(msg.type()).isEqualTo(SessionEventType.FAILED);
        assertThat(msg).isInstanceOfSatisfying(SessionProcessingFailedMessage.class,
                failed -> assertThat(failed.reason()).isEqualTo("Quota exceeded"));
    }

    /** Verifies the broadcast went to the canonical session topic and returns the captured message. */
    private SessionRealtimeMessage captureMessage() {
        var captor = ArgumentCaptor.forClass(SessionRealtimeMessage.class);
        verify(notifier).broadcast(eq(SessionTopics.of(sessionId)), captor.capture());
        SessionRealtimeMessage msg = captor.getValue();
        assertThat(msg.sessionId()).isEqualTo(sessionId);
        return msg;
    }
}
