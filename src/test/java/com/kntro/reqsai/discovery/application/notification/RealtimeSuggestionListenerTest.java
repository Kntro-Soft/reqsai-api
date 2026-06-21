package com.kntro.reqsai.discovery.application.notification;

import com.kntro.reqsai.discovery.application.service.RealtimeSuggestionService;
import com.kntro.reqsai.discovery.domain.event.TranscriptSegmentAppendedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RealtimeSuggestionListener}: windowing logic, partial-segment filtering,
 * and exception isolation.
 */
@DisplayName("Realtime: RealtimeSuggestionListener")
@ExtendWith(MockitoExtension.class)
class RealtimeSuggestionListenerTest {

    @Mock
    private RealtimeSuggestionService suggestionService;

    @InjectMocks
    private RealtimeSuggestionListener listener;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(listener, "suggestionWindow", 5);
    }

    private TranscriptSegmentAppendedEvent event(int sequence, boolean isFinal) {
        return TranscriptSegmentAppendedEvent.of(UUID.randomUUID(), sequence, null, "text", 0L, 100L, isFinal);
    }

    @Test
    @DisplayName("should trigger suggestion at window boundary (sequence % window == 0)")
    void should_trigger_at_window_boundary() {
        UUID sessionId = UUID.randomUUID();
        var evt = TranscriptSegmentAppendedEvent.of(sessionId, 5, null, "text", 0L, 100L, true);

        listener.onSegmentAppended(evt);

        verify(suggestionService).suggest(sessionId);
    }

    @Test
    @DisplayName("should trigger at every subsequent window boundary")
    void should_trigger_at_10_and_15() {
        listener.onSegmentAppended(event(10, true));
        listener.onSegmentAppended(event(15, true));

        verify(suggestionService, times(2)).suggest(any());
    }

    @Test
    @DisplayName("should not trigger for partial segments (isFinal=false)")
    void should_ignore_partial_segments() {
        listener.onSegmentAppended(event(5, false));

        verifyNoInteractions(suggestionService);
    }

    @Test
    @DisplayName("should not trigger when sequence is not at window boundary")
    void should_not_trigger_between_boundaries() {
        listener.onSegmentAppended(event(1, true));
        listener.onSegmentAppended(event(2, true));
        listener.onSegmentAppended(event(3, true));
        listener.onSegmentAppended(event(4, true));

        verifyNoInteractions(suggestionService);
    }

    @Test
    @DisplayName("should swallow exception so transcript pipeline is never disrupted")
    void should_swallow_exception() {
        UUID sessionId = UUID.randomUUID();
        var evt = TranscriptSegmentAppendedEvent.of(sessionId, 5, null, "text", 0L, 100L, true);
        doThrow(new RuntimeException("LLM timeout")).when(suggestionService).suggest(sessionId);

        listener.onSegmentAppended(evt); // must not throw

        verify(suggestionService).suggest(sessionId);
    }
}
