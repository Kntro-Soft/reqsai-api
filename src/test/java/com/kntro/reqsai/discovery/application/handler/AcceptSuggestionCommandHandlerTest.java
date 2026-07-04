package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.AcceptSuggestionCommand;
import com.kntro.reqsai.discovery.application.port.SuggestionRepository;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.SuggestionStatus;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AcceptSuggestionCommandHandler}, focused on the acceptance of a
 * {@code NEW_STORY} suggestion and the resilience of the (optional) embedding step.
 *
 * @see AcceptSuggestionCommandHandler
 */
@DisplayName("Application: Accept Suggestion")
@ExtendWith(MockitoExtension.class)
class AcceptSuggestionCommandHandlerTest {

    @Mock
    private SuggestionRepository suggestions;
    @Mock
    private UserStoryRepository storyRepo;
    @Mock
    private EmbeddingPort embeddingPort;
    @InjectMocks
    private AcceptSuggestionCommandHandler handler;

    private static Suggestion pendingNewStory() {
        return Suggestion.newStory(UUID.randomUUID(), UUID.randomUUID(),
                "Login", "user", "log in", "access the app", Priority.HIGH, 3);
    }

    @Test
    @DisplayName("should create a new story and embed it when the embedding port is available")
    void should_create_and_embed_new_story() {
        // Arrange
        Suggestion suggestion = pendingNewStory();
        when(suggestions.findByIdAndSessionIdForUpdate(any(), any())).thenReturn(Optional.of(suggestion));
        when(embeddingPort.isAvailable()).thenReturn(true);
        when(embeddingPort.embed(any())).thenReturn(new float[EmbeddingPort.DIMENSIONS]);
        when(storyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Suggestion result = handler.handle(command(suggestion));

        // Assert — story persisted with an embedding, suggestion accepted
        var captor = ArgumentCaptor.forClass(UserStory.class);
        verify(storyRepo).save(captor.capture());
        assertThat(captor.getValue().isIndexed()).isTrue();
        assertThat(result.getStatus()).isEqualTo(SuggestionStatus.ACCEPTED);
        assertThat(result.getResolvedStoryId()).isNotNull();
    }

    @Test
    @DisplayName("should still accept and persist the story when the embedding call fails (first-try resilience)")
    void should_accept_when_embedding_fails() {
        // Arrange — the embedding provider throws on this (first) call, as a cold model/timeout would
        Suggestion suggestion = pendingNewStory();
        when(suggestions.findByIdAndSessionIdForUpdate(any(), any())).thenReturn(Optional.of(suggestion));
        when(embeddingPort.isAvailable()).thenReturn(true);
        when(embeddingPort.embed(any())).thenThrow(new RuntimeException("provider timed out"));
        when(storyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act — must NOT propagate the embedding failure (no 500); the accept commits
        Suggestion result = handler.handle(command(suggestion));

        // Assert — story persisted un-indexed, suggestion still accepted
        var captor = ArgumentCaptor.forClass(UserStory.class);
        verify(storyRepo).save(captor.capture());
        assertThat(captor.getValue().isIndexed()).isFalse();
        assertThat(result.getStatus()).isEqualTo(SuggestionStatus.ACCEPTED);
        assertThat(result.getResolvedStoryId()).isNotNull();
    }

    private static AcceptSuggestionCommand command(Suggestion s) {
        return new AcceptSuggestionCommand(s.getSessionId(), s.getId(), null, null, null, null, null, null);
    }
}
