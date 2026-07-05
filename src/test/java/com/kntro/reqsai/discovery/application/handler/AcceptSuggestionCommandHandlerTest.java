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

    @Test
    @DisplayName("should cap the composed edge-case scenario label so a max-length title cannot fail the accept")
    void should_cap_edge_case_scenario_label() {
        // Arrange — an EDGE_CASE whose draft title is at the 200-char column limit
        String maxTitle = "x".repeat(200);
        UUID projectId = UUID.randomUUID();
        UserStory target = new UserStory(UUID.randomUUID(), projectId,
                "Login", "user", "log in", "access the app", Priority.HIGH, 3);
        Suggestion suggestion = Suggestion.edgeCase(UUID.randomUUID(), projectId,
                maxTitle, "user", "handle unverified accounts", "avoid lockouts",
                Priority.MEDIUM, 2, null, target.getId());
        when(suggestions.findByIdAndSessionIdForUpdate(any(), any())).thenReturn(Optional.of(suggestion));
        when(storyRepo.findById(target.getId())).thenReturn(Optional.of(target));
        when(storyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act — must not throw a length validation error
        Suggestion result = handler.handle(command(suggestion));

        // Assert — criterion added with a capped scenario label
        assertThat(result.getStatus()).isEqualTo(SuggestionStatus.ACCEPTED);
        assertThat(target.getAcceptanceCriteria()).hasSize(1);
        assertThat(target.getAcceptanceCriteria().getFirst().getScenario())
                .hasSize(200)
                .startsWith("Edge case: ");
    }

    @Test
    @DisplayName("should create the story WITH the draft acceptance criteria on accept")
    void should_create_story_with_draft_criteria() {
        Suggestion suggestion = Suggestion.newStory(UUID.randomUUID(), UUID.randomUUID(),
                "Iniciar sesión", "usuario", "iniciar sesión", "acceder", Priority.HIGH, 3,
                java.util.List.of(
                        new Suggestion.DraftCriterion("Credenciales válidas",
                                "el usuario tiene una cuenta", "ingresa credenciales correctas", "accede al sistema"),
                        new Suggestion.DraftCriterion(null,
                                "la contraseña es incorrecta", "intenta iniciar sesión", "ve un mensaje de error")));
        when(suggestions.findByIdAndSessionIdForUpdate(any(), any())).thenReturn(Optional.of(suggestion));
        when(embeddingPort.isAvailable()).thenReturn(false);
        when(storyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler.handle(command(suggestion));

        var captor = ArgumentCaptor.forClass(UserStory.class);
        verify(storyRepo).save(captor.capture());
        assertThat(captor.getValue().getAcceptanceCriteria()).hasSize(2);
        assertThat(captor.getValue().getAcceptanceCriteria().getFirst().getScenario())
                .isEqualTo("Credenciales válidas");
        assertThat(captor.getValue().getAcceptanceCriteria().getLast().getScenario()).isNull();
    }

    private static AcceptSuggestionCommand command(Suggestion s) {
        return new AcceptSuggestionCommand(s.getSessionId(), s.getId(), null, null, null, null, null, null);
    }
}
