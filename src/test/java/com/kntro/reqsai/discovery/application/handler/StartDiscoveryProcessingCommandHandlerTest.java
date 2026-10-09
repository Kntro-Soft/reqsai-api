package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.StartDiscoveryProcessingCommand;
import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.GenerationResult;
import com.kntro.reqsai.discovery.application.port.RequirementGenerationPort;
import com.kntro.reqsai.discovery.application.port.TranscriptSegmentRepository;
import com.kntro.reqsai.discovery.application.service.SessionSpeakerService;
import com.kntro.reqsai.discovery.application.service.QuoteLocator;
import com.kntro.reqsai.discovery.application.service.StoryExtractionService;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryError;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.SessionSpeaker;
import com.kntro.reqsai.discovery.domain.model.SessionStatus;
import com.kntro.reqsai.discovery.domain.model.SpeakerRoster;
import com.kntro.reqsai.discovery.domain.model.SpeakerSide;
import com.kntro.reqsai.discovery.domain.model.SpeakerSpan;
import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.discovery.mothers.DiscoverySessionMother;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.shared.domain.exception.DomainException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link StartDiscoveryProcessingCommandHandler}.
 * Story creation/dedup logic is tested separately in {@code StoryExtractionServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Application: Start Discovery Processing")
class StartDiscoveryProcessingCommandHandlerTest {

    @Mock
    private DiscoverySessionRepository sessions;
    @Mock
    private RequirementGenerationPort requirementGeneration;
    @Mock
    private StoryExtractionService storyExtraction;
    @Mock
    private TranscriptSegmentRepository segments;
    @Mock
    private SessionSpeakerService sessionSpeakers;
    @InjectMocks
    private StartDiscoveryProcessingCommandHandler handler;

    @Test
    @DisplayName("should process transcript and create stories")
    void should_process_and_create_stories() {
        // Arrange
        DiscoverySession session = DiscoverySessionMother.draft().build();
        session.uploadTranscript("El cliente quiere login con Google.", 0L);
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        when(sessions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        GenerationResult generationResult = new GenerationResult(List.of(
                new GenerationResult.GeneratedStory("Login Google", "usuario", "iniciar sesión", "sin contraseña",
                        Priority.HIGH, 3, List.of())));
        when(requirementGeneration.generate(any(), any())).thenReturn(generationResult);
        UserStory mockStory = mock(UserStory.class);
        when(storyExtraction.extractOne(any(), any(), any(), any(QuoteLocator.class))).thenReturn(Optional.of(mockStory));

        // Act
        var outcome = handler.handle(new StartDiscoveryProcessingCommand(session.getId()));

        // Assert
        assertThat(outcome.session().getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(outcome.stories()).hasSize(1);
        verify(requirementGeneration).generate(any(), any());
        verify(storyExtraction).extractOne(eq(generationResult.stories().getFirst()), eq(session.getId()),
                eq(session.getProjectId()), any(QuoteLocator.class));
    }

    @Test
    @DisplayName("should send a diarized transcript as speaker turns tagged with the names and sides (US40)")
    void should_send_speaker_tagged_transcript() {
        // Arrange — an uploaded recording whose provider labelled two speakers; the analyst named the client
        DiscoverySession session = DiscoverySessionMother.draft().build();
        session.uploadTranscript("Quiero reservar una mesa. ¿Para cuántas personas? Para cuatro.", 9_000L, 3);
        UUID sessionId = session.getId();
        List<TranscriptSegment> finals = List.of(
                new TranscriptSegment(sessionId, 1, "0", "Quiero reservar una mesa.", 0, 2_000, true),
                new TranscriptSegment(sessionId, 2, "1", "¿Para cuántas personas?", 2_100, 4_000, true),
                new TranscriptSegment(sessionId, 3, "0", "Para cuatro.", 4_100, 5_000, true));
        SessionSpeaker client = new SessionSpeaker(sessionId, "0");
        client.describe("Ana", SpeakerSide.CLIENT);
        SpeakerRoster roster = SpeakerRoster.of(
                finals.stream().map(f -> new SpeakerSpan(f.getSpeakerLabel(), f.getStartMs(), f.getEndMs())).toList(),
                List.of(client));
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(segments.findAllBySessionId(sessionId)).thenReturn(finals);
        when(sessionSpeakers.rosterOf(sessionId)).thenReturn(roster);
        when(requirementGeneration.generate(any(), any())).thenReturn(new GenerationResult(List.of()));

        // Act
        handler.handle(new StartDiscoveryProcessingCommand(sessionId));

        // Assert
        verify(requirementGeneration).generate(
                "[Ana (Cliente)]: Quiero reservar una mesa.\n"
                        + "[Hablante 2]: ¿Para cuántas personas?\n"
                        + "[Ana (Cliente)]: Para cuatro.",
                session.getLanguage().value());
    }

    @Test
    @DisplayName("should send the stored transcript when its segments carry no speakers")
    void should_send_stored_transcript_without_speakers() {
        DiscoverySession session = DiscoverySessionMother.draft().build();
        session.uploadTranscript("El cliente quiere login con Google.", 0L);
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        when(sessions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(requirementGeneration.generate(any(), any())).thenReturn(new GenerationResult(List.of()));

        handler.handle(new StartDiscoveryProcessingCommand(session.getId()));

        verify(requirementGeneration).generate("El cliente quiere login con Google.", session.getLanguage().value());
        verifyNoInteractions(sessionSpeakers);
    }

    @Test
    @DisplayName("should throw SESSION_NOT_FOUND when session does not exist")
    void should_throw_when_session_not_found() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(sessions.findById(id)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(new StartDiscoveryProcessingCommand(id)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("should throw INVALID_SESSION_STATUS when session is COMPLETED (not STOPPED or FAILED)")
    void should_throw_when_session_in_wrong_status() {
        // Arrange — COMPLETED has a transcript but is in a terminal state
        DiscoverySession session = DiscoverySessionMother.draft().build();
        session.uploadTranscript("Some transcript.", 0L);
        session.startProcessing();
        session.complete();
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(new StartDiscoveryProcessingCommand(session.getId())))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).error())
                        .isEqualTo(DiscoveryError.INVALID_SESSION_STATUS));
    }

    @Test
    @DisplayName("should mark session FAILED when generation throws")
    void should_mark_failed_on_generation_error() {
        // Arrange
        DiscoverySession session = DiscoverySessionMother.draft().build();
        session.uploadTranscript("Transcript.", 0L);
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        when(sessions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(requirementGeneration.generate(any(), any())).thenThrow(new RuntimeException("API timeout"));

        // Act
        var outcome = handler.handle(new StartDiscoveryProcessingCommand(session.getId()));

        // Assert
        assertThat(outcome.session().getStatus()).isEqualTo(SessionStatus.FAILED);
        assertThat(outcome.session().getProcessingError()).contains("timeout");
        assertThat(outcome.stories()).isEmpty();
    }

    @Test
    @DisplayName("should complete with zero stories when all are duplicates")
    void should_complete_when_all_stories_are_duplicates() {
        // Arrange
        DiscoverySession session = DiscoverySessionMother.draft().build();
        session.uploadTranscript("Transcript.", 0L);
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        when(sessions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        GenerationResult generationResult = new GenerationResult(List.of(
                new GenerationResult.GeneratedStory("Dup", "u", "a", "b", Priority.HIGH, 3, List.of())));
        when(requirementGeneration.generate(any(), any())).thenReturn(generationResult);
        when(storyExtraction.extractOne(any(), any(), any(), any(QuoteLocator.class))).thenReturn(Optional.empty());

        // Act
        var outcome = handler.handle(new StartDiscoveryProcessingCommand(session.getId()));

        // Assert
        assertThat(outcome.session().getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(outcome.stories()).isEmpty();
    }
}
