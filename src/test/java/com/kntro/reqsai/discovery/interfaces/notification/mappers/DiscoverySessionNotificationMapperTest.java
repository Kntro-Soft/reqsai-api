package com.kntro.reqsai.discovery.interfaces.notification.mappers;

import com.kntro.reqsai.discovery.domain.event.DiscoverySessionProcessingCompletedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionProcessingFailedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionProcessingStartedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionRecordingPausedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionRecordingResumedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionRecordingStartedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionRecordingStoppedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionResetEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionTranscriptUploadedEvent;
import com.kntro.reqsai.discovery.interfaces.notification.SessionEventType;
import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionProcessingFailedMessage;
import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionStatusChangedMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the session-event → WebSocket-message mapping. Every event must produce the right
 * message subtype, the right {@link SessionEventType} discriminator, and must forward {@code sessionId}
 * and {@code occurredAt} unchanged.
 *
 * @see DiscoverySessionNotificationMapper
 */
@DisplayName("Realtime: DiscoverySessionNotificationMapper")
class DiscoverySessionNotificationMapperTest {

    private final UUID sessionId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    @Test
    @DisplayName("should map recording lifecycle events to the matching status-changed type")
    void should_map_recording_events() {
        // Arrange
        var started = DiscoverySessionRecordingStartedEvent.of(sessionId, projectId);
        var paused = DiscoverySessionRecordingPausedEvent.of(sessionId, projectId);
        var resumed = DiscoverySessionRecordingResumedEvent.of(sessionId, projectId);
        var stopped = DiscoverySessionRecordingStoppedEvent.of(sessionId, projectId);
        var reset = DiscoverySessionResetEvent.of(sessionId, projectId);

        // Act & Assert
        assertStatus(DiscoverySessionNotificationMapper.toMessage(started), SessionEventType.RECORDING_STARTED, started.occurredAt());
        assertStatus(DiscoverySessionNotificationMapper.toMessage(paused), SessionEventType.RECORDING_PAUSED, paused.occurredAt());
        assertStatus(DiscoverySessionNotificationMapper.toMessage(resumed), SessionEventType.RECORDING_RESUMED, resumed.occurredAt());
        assertStatus(DiscoverySessionNotificationMapper.toMessage(stopped), SessionEventType.RECORDING_STOPPED, stopped.occurredAt());
        assertStatus(DiscoverySessionNotificationMapper.toMessage(reset), SessionEventType.SESSION_RESET, reset.occurredAt());
    }

    @Test
    @DisplayName("should map AI processing events to the matching status-changed type")
    void should_map_processing_events() {
        // Arrange
        var uploaded = DiscoverySessionTranscriptUploadedEvent.of(sessionId, projectId);
        var processing = DiscoverySessionProcessingStartedEvent.of(sessionId, projectId);
        var completed = DiscoverySessionProcessingCompletedEvent.of(sessionId, projectId);

        // Act & Assert
        assertStatus(DiscoverySessionNotificationMapper.toMessage(uploaded), SessionEventType.TRANSCRIPT_UPLOADED, uploaded.occurredAt());
        assertStatus(DiscoverySessionNotificationMapper.toMessage(processing), SessionEventType.PROCESSING, processing.occurredAt());
        assertStatus(DiscoverySessionNotificationMapper.toMessage(completed), SessionEventType.COMPLETED, completed.occurredAt());
    }

    @Test
    @DisplayName("should map a processing-failed event to a failed message carrying the reason")
    void should_map_failed_event() {
        // Arrange
        var failed = DiscoverySessionProcessingFailedEvent.of(sessionId, projectId, "Quota exceeded");

        // Act
        SessionProcessingFailedMessage msg = DiscoverySessionNotificationMapper.toMessage(failed);

        // Assert
        assertThat(msg.sessionId()).isEqualTo(sessionId);
        assertThat(msg.type()).isEqualTo(SessionEventType.FAILED);
        assertThat(msg.reason()).isEqualTo("Quota exceeded");
        assertThat(msg.occurredAt()).isEqualTo(failed.occurredAt());
    }

    private void assertStatus(SessionStatusChangedMessage msg, SessionEventType expectedType, java.time.Instant expectedOccurredAt) {
        assertThat(msg.sessionId()).isEqualTo(sessionId);
        assertThat(msg.type()).isEqualTo(expectedType);
        assertThat(msg.occurredAt()).isEqualTo(expectedOccurredAt);
    }
}
