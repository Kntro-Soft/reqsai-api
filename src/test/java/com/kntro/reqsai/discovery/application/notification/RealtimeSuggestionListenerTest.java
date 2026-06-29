package com.kntro.reqsai.discovery.application.notification;

import com.kntro.reqsai.discovery.application.service.RealtimeSuggestionService;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionRecordingStoppedEvent;
import com.kntro.reqsai.discovery.domain.event.TranscriptSegmentAppendedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RealtimeSuggestionListener}: fires on every final segment (the service
 * gates on the watermark/accrued text), ignores partials, flushes on stop, and isolates exceptions.
 */
@DisplayName("Realtime: RealtimeSuggestionListener")
@ExtendWith(MockitoExtension.class)
class RealtimeSuggestionListenerTest {

    @Mock
    private RealtimeSuggestionService suggestionService;

    @InjectMocks
    private RealtimeSuggestionListener listener;

    private TranscriptSegmentAppendedEvent segment(UUID sessionId, int sequence, boolean isFinal) {
        return TranscriptSegmentAppendedEvent.of(sessionId, sequence, null, "text", 0L, 100L, isFinal);
    }

    @Test
    @DisplayName("should delegate to the service on every finalized segment")
    void should_trigger_on_every_final_segment() {
        UUID sessionId = UUID.randomUUID();

        listener.onSegmentAppended(segment(sessionId, 1, true));

        verify(suggestionService).suggest(sessionId);
    }

    @Test
    @DisplayName("should not trigger for partial segments (isFinal=false)")
    void should_ignore_partial_segments() {
        listener.onSegmentAppended(segment(UUID.randomUUID(), 5, false));

        verifyNoInteractions(suggestionService);
    }

    @Test
    @DisplayName("should flush the remaining tail when recording stops")
    void should_flush_on_stop() {
        UUID sessionId = UUID.randomUUID();

        listener.onRecordingStopped(DiscoverySessionRecordingStoppedEvent.of(sessionId, UUID.randomUUID()));

        verify(suggestionService).suggest(sessionId, true);
    }

    @Test
    @DisplayName("should swallow exception so the transcript pipeline is never disrupted")
    void should_swallow_exception() {
        UUID sessionId = UUID.randomUUID();
        doThrow(new RuntimeException("LLM timeout")).when(suggestionService).suggest(sessionId);

        listener.onSegmentAppended(segment(sessionId, 5, true)); // must not throw

        verify(suggestionService).suggest(sessionId);
    }
}
