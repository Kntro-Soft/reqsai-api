package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.AppendTranscriptSegmentCommand;
import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.TranscriptSegmentRepository;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import com.kntro.reqsai.discovery.mothers.DiscoverySessionMother;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the append-segment use case: a final transcript event persists a segment with the
 * session-assigned sequence and saves the session (so the appended event is published).
 *
 * @see AppendTranscriptSegmentCommandHandler
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Application: Append Transcript Segment")
class AppendTranscriptSegmentCommandHandlerTest {

    @Mock
    private DiscoverySessionRepository sessions;
    @Mock
    private TranscriptSegmentRepository segments;
    @InjectMocks
    private AppendTranscriptSegmentCommandHandler handler;

    @Test
    @DisplayName("should persist the final segment with the next sequence and save the session")
    void should_append_final_segment() {
        // Arrange
        DiscoverySession session = DiscoverySessionMother.draft().build();
        session.startRecording(Instant.now());
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        when(segments.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var command = new AppendTranscriptSegmentCommand(session.getId(), "Necesito un login", "0", 0, 1200, true);

        // Act
        handler.handle(command);

        // Assert
        var captor = ArgumentCaptor.forClass(TranscriptSegment.class);
        verify(segments).save(captor.capture());
        TranscriptSegment persisted = captor.getValue();
        assertThat(persisted.getSequence()).isEqualTo(1);
        assertThat(persisted.getText()).isEqualTo("Necesito un login");
        assertThat(persisted.getSpeakerLabel()).isEqualTo("0");
        assertThat(persisted.getSessionId()).isEqualTo(session.getId());
        assertThat(persisted.isFinal()).isTrue();
        verify(sessions).save(session);
        assertThat(session.getLastSequence()).isEqualTo(1);
    }

    @Test
    @DisplayName("should NOT persist a partial segment — fires event only, no DB row")
    void should_skip_db_save_for_partial_segment() {
        // Arrange
        DiscoverySession session = DiscoverySessionMother.draft().build();
        session.startRecording(Instant.now());
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        var command = new AppendTranscriptSegmentCommand(session.getId(), "Nece...", null, 0, 800, false);

        // Act
        handler.handle(command);

        // Assert — no DB writes; event was fired inside session.recordSegment()
        verify(segments, never()).save(any());
        verify(sessions, never()).save(any());
        assertThat(session.getLastSequence()).isEqualTo(0); // sequence not advanced for partials
    }

    @Test
    @DisplayName("should throw when the session does not exist")
    void should_throw_when_session_missing() {
        UUID missing = UUID.randomUUID();
        when(sessions.findById(missing)).thenReturn(Optional.empty());
        var command = new AppendTranscriptSegmentCommand(missing, "x", null, 0, 1, true);

        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("should reject appending when the session is not RECORDING")
    void should_reject_when_not_recording() {
        DiscoverySession session = DiscoverySessionMother.draft().build(); // DRAFT, not RECORDING
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        var command = new AppendTranscriptSegmentCommand(session.getId(), "x", null, 0, 1, true);

        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(DomainException.class);
    }
}
