package com.kntro.reqsai.discovery.interfaces.notification.mappers;

import com.kntro.reqsai.discovery.domain.event.TranscriptSegmentAppendedEvent;
import com.kntro.reqsai.discovery.interfaces.notification.SessionEventType;
import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionTranscriptSegmentMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the transcript-segment event → WebSocket message mapping (forwards every field).
 *
 * @see TranscriptSegmentNotificationMapper
 */
@DisplayName("Realtime: TranscriptSegmentNotificationMapper")
class TranscriptSegmentNotificationMapperTest {

    @Test
    @DisplayName("should map a segment-appended event, forwarding all fields and the TRANSCRIPT_SEGMENT type")
    void should_map_event() {
        // Arrange
        UUID sessionId = UUID.randomUUID();
        var event = TranscriptSegmentAppendedEvent.of(sessionId, 3, "1", "Con Google también", 1500, 3000, true);

        // Act
        SessionTranscriptSegmentMessage msg = TranscriptSegmentNotificationMapper.toMessage(event);

        // Assert
        assertThat(msg.sessionId()).isEqualTo(sessionId);
        assertThat(msg.type()).isEqualTo(SessionEventType.TRANSCRIPT_SEGMENT);
        assertThat(msg.sequence()).isEqualTo(3);
        assertThat(msg.speakerLabel()).isEqualTo("1");
        assertThat(msg.text()).isEqualTo("Con Google también");
        assertThat(msg.startMs()).isEqualTo(1500);
        assertThat(msg.endMs()).isEqualTo(3000);
        assertThat(msg.isFinal()).isTrue();
        assertThat(msg.occurredAt()).isEqualTo(event.occurredAt());
    }
}
