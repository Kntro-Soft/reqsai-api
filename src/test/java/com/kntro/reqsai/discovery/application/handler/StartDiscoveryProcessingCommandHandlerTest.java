package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.StartDiscoveryProcessingCommand;
import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.GenerationResult;
import com.kntro.reqsai.discovery.application.port.RequirementGenerationPort;
import com.kntro.reqsai.discovery.application.service.StoryExtractionService;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryError;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.SessionStatus;
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
        when(storyExtraction.extractOne(any(), any(), any())).thenReturn(Optional.of(mockStory));

        // Act
        var outcome = handler.handle(new StartDiscoveryProcessingCommand(session.getId()));

        // Assert
        assertThat(outcome.session().getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(outcome.stories()).hasSize(1);
        verify(requirementGeneration).generate(any(), any());
        verify(storyExtraction).extractOne(
                generationResult.stories().getFirst(), session.getId(), session.getProjectId());
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
        when(storyExtraction.extractOne(any(), any(), any())).thenReturn(Optional.empty());

        // Act
        var outcome = handler.handle(new StartDiscoveryProcessingCommand(session.getId()));

        // Assert
        assertThat(outcome.session().getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(outcome.stories()).isEmpty();
    }
}
