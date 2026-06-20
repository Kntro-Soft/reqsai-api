package com.kntro.reqsai.discovery.application.notification;

import com.kntro.reqsai.discovery.domain.event.TranscriptSegmentAppendedEvent;
import com.kntro.reqsai.discovery.interfaces.notification.SessionEventType;
import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionTranscriptSegmentMessage;
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
 * Unit tests for the transcript-segment listener: a segment-appended event is broadcast to the session
 * topic as a {@link SessionTranscriptSegmentMessage}.
 *
 * @see TranscriptSegmentNotificationListener
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Realtime: TranscriptSegmentNotificationListener")
class TranscriptSegmentNotificationListenerTest {

    @Mock
    private RealtimeNotifier notifier;
    @InjectMocks
    private TranscriptSegmentNotificationListener listener;

    @Test
    @DisplayName("should broadcast TRANSCRIPT_SEGMENT to the session topic with the segment payload")
    void should_notify_segment() {
        // Arrange
        UUID sessionId = UUID.randomUUID();
        var event = TranscriptSegmentAppendedEvent.of(sessionId, 2, "0", "Hola", 0, 800, true);

        // Act
        listener.onSegmentAppended(event);

        // Assert
        var captor = ArgumentCaptor.forClass(SessionTranscriptSegmentMessage.class);
        verify(notifier).broadcast(eq(SessionTopics.of(sessionId)), captor.capture());
        SessionTranscriptSegmentMessage msg = captor.getValue();
        assertThat(msg.sessionId()).isEqualTo(sessionId);
        assertThat(msg.type()).isEqualTo(SessionEventType.TRANSCRIPT_SEGMENT);
        assertThat(msg.sequence()).isEqualTo(2);
        assertThat(msg.text()).isEqualTo("Hola");
    }
}
