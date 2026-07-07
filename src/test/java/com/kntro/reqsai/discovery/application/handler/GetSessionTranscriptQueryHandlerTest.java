package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.TranscriptSegmentRepository;
import com.kntro.reqsai.discovery.application.query.GetSessionTranscriptQuery;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import com.kntro.reqsai.discovery.mothers.DiscoverySessionMother;
import com.kntro.reqsai.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GetSessionTranscriptQueryHandler}: a live-captured session whose stored
 * {@code transcript} field is blank must have its conversation assembled from the persisted final
 * {@link TranscriptSegment} rows, one segment per line, in ascending sequence order.
 *
 * @see GetSessionTranscriptQueryHandler
 */
@DisplayName("Application: Get Session Transcript")
@ExtendWith(MockitoExtension.class)
class GetSessionTranscriptQueryHandlerTest {

    @Mock
    private DiscoverySessionRepository sessions;
    @Mock
    private TranscriptSegmentRepository segments;
    @InjectMocks
    private GetSessionTranscriptQueryHandler handler;

    @Test
    @DisplayName("should assemble the transcript from final segments when the stored field is blank")
    void should_assemble_from_segments_when_blank() {
        // Arrange — a session that was only captured live (blank transcript field)
        DiscoverySession session = DiscoverySessionMother.draft().build();
        UUID sessionId = session.getId();
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
        when(segments.findAllBySessionId(sessionId)).thenReturn(List.of(
                segment(sessionId, 1, "Necesito un login", true),
                segment(sessionId, 2, "con recuperación de contraseña", true)));

        // Act
        String transcript = handler.handle(new GetSessionTranscriptQuery(sessionId));

        // Assert — one segment per line, in order
        assertThat(transcript).isEqualTo("Necesito un login\ncon recuperación de contraseña");
    }

    @Test
    @DisplayName("should ignore non-final segments when assembling")
    void should_ignore_partial_segments() {
        DiscoverySession session = DiscoverySessionMother.draft().build();
        UUID sessionId = session.getId();
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
        when(segments.findAllBySessionId(sessionId)).thenReturn(List.of(
                segment(sessionId, 1, "Final one", true),
                segment(sessionId, 0, "partial hypothesis", false),
                segment(sessionId, 2, "Final two", true)));

        String transcript = handler.handle(new GetSessionTranscriptQuery(sessionId));

        assertThat(transcript).isEqualTo("Final one\nFinal two");
    }

    @Test
    @DisplayName("should return the stored transcript verbatim without querying segments when it is present")
    void should_return_stored_transcript_when_present() {
        DiscoverySession session = DiscoverySessionMother.draft().build();
        session.uploadTranscript("Already processed transcript text", 60_000);
        UUID sessionId = session.getId();
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));

        String transcript = handler.handle(new GetSessionTranscriptQuery(sessionId));

        assertThat(transcript).isEqualTo("Already processed transcript text");
        verify(segments, never()).findAllBySessionId(sessionId);
    }

    @Test
    @DisplayName("should throw when the session does not exist")
    void should_throw_when_session_missing() {
        UUID missing = UUID.randomUUID();
        when(sessions.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new GetSessionTranscriptQuery(missing)))
                .isInstanceOf(EntityNotFoundException.class);
        verifyNoInteractions(segments);
    }

    private static TranscriptSegment segment(UUID sessionId, int sequence, String text, boolean isFinal) {
        return new TranscriptSegment(sessionId, sequence, "0", text, 0, 100, isFinal);
    }
}
