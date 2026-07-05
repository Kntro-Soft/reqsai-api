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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AcceptSuggestionCommandHandler}: NEW_STORY / EDGE_CASE acceptance, the
 * full edit-before-accept payload, and resilience of the (optional) embedding step.
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
        Suggestion suggestion = pendingNewStory();
        when(suggestions.findByIdAndSessionIdForUpdate(any(), any())).thenReturn(Optional.of(suggestion));
        when(embeddingPort.isAvailable()).thenReturn(true);
        when(embeddingPort.embed(any())).thenReturn(new float[EmbeddingPort.DIMENSIONS]);
        when(storyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Suggestion result = handler.handle(accept(suggestion));

        var captor = ArgumentCaptor.forClass(UserStory.class);
        verify(storyRepo).save(captor.capture());
        assertThat(captor.getValue().isIndexed()).isTrue();
        assertThat(result.getStatus()).isEqualTo(SuggestionStatus.ACCEPTED);
        assertThat(result.getResolvedStoryId()).isNotNull();
    }

    @Test
    @DisplayName("should still accept and persist the story when the embedding call fails (first-try resilience)")
    void should_accept_when_embedding_fails() {
        Suggestion suggestion = pendingNewStory();
        when(suggestions.findByIdAndSessionIdForUpdate(any(), any())).thenReturn(Optional.of(suggestion));
        when(embeddingPort.isAvailable()).thenReturn(true);
        when(embeddingPort.embed(any())).thenThrow(new RuntimeException("provider timed out"));
        when(storyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Suggestion result = handler.handle(accept(suggestion));

        var captor = ArgumentCaptor.forClass(UserStory.class);
        verify(storyRepo).save(captor.capture());
        assertThat(captor.getValue().isIndexed()).isFalse();
        assertThat(result.getStatus()).isEqualTo(SuggestionStatus.ACCEPTED);
        assertThat(result.getResolvedStoryId()).isNotNull();
    }

    @Test
    @DisplayName("EDGE_CASE accept adds the real Given/When/Then criterion to the target story (no fabrication)")
    void should_add_real_edge_case_criterion_to_target() {
        UUID projectId = UUID.randomUUID();
        UserStory target = new UserStory(UUID.randomUUID(), projectId,
                "Login", "user", "log in", "access the app", Priority.HIGH, 3);
        Suggestion suggestion = Suggestion.edgeCase(UUID.randomUUID(), projectId,
                "Cuenta bloqueada", "usuario", "iniciar sesión", "acceder",
                Priority.MEDIUM, 2, "inicio de sesión", target.getId(),
                new Suggestion.DraftCriterion("Bloqueo por intentos",
                        "el usuario falló 5 intentos", "intenta iniciar sesión de nuevo",
                        "el sistema bloquea la cuenta 15 minutos"));
        when(suggestions.findByIdAndSessionIdForUpdate(any(), any())).thenReturn(Optional.of(suggestion));
        when(storyRepo.findById(target.getId())).thenReturn(Optional.of(target));
        when(storyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Suggestion result = handler.handle(accept(suggestion));

        assertThat(result.getStatus()).isEqualTo(SuggestionStatus.ACCEPTED);
        assertThat(target.getAcceptanceCriteria()).hasSize(1);
        var criterion = target.getAcceptanceCriteria().getFirst();
        assertThat(criterion.getScenario()).isEqualTo("Bloqueo por intentos");
        assertThat(criterion.getGiven()).isEqualTo("el usuario falló 5 intentos");
        assertThat(criterion.getWhen()).isEqualTo("intenta iniciar sesión de nuevo");
        assertThat(criterion.getThen()).isEqualTo("el sistema bloquea la cuenta 15 minutos");
    }

    @Test
    @DisplayName("should create the story WITH the draft acceptance criteria on accept")
    void should_create_story_with_draft_criteria() {
        Suggestion suggestion = Suggestion.newStory(UUID.randomUUID(), UUID.randomUUID(),
                "Iniciar sesión", "usuario", "iniciar sesión", "acceder", Priority.HIGH, 3,
                List.of(
                        new Suggestion.DraftCriterion("Credenciales válidas",
                                "el usuario tiene una cuenta", "ingresa credenciales correctas", "accede al sistema"),
                        new Suggestion.DraftCriterion(null,
                                "la contraseña es incorrecta", "intenta iniciar sesión", "ve un mensaje de error")));
        when(suggestions.findByIdAndSessionIdForUpdate(any(), any())).thenReturn(Optional.of(suggestion));
        when(embeddingPort.isAvailable()).thenReturn(false);
        when(storyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler.handle(accept(suggestion));

        var captor = ArgumentCaptor.forClass(UserStory.class);
        verify(storyRepo).save(captor.capture());
        assertThat(captor.getValue().getAcceptanceCriteria()).hasSize(2);
        assertThat(captor.getValue().getAcceptanceCriteria().getFirst().getScenario())
                .isEqualTo("Credenciales válidas");
        assertThat(captor.getValue().getAcceptanceCriteria().getLast().getScenario()).isNull();
    }

    @Test
    @DisplayName("NEW_STORY accept with an edited payload persists the edited title and replaced criteria, not the draft")
    void should_persist_edited_new_story_payload() {
        Suggestion suggestion = Suggestion.newStory(UUID.randomUUID(), UUID.randomUUID(),
                "Draft title", "user", "do the draft action", "the draft benefit", Priority.LOW, 1,
                List.of(new Suggestion.DraftCriterion("draft label",
                        "draft given", "draft when", "draft then")));
        when(suggestions.findByIdAndSessionIdForUpdate(any(), any())).thenReturn(Optional.of(suggestion));
        when(embeddingPort.isAvailable()).thenReturn(false);
        when(storyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler.handle(new AcceptSuggestionCommand(
                suggestion.getSessionId(), suggestion.getId(),
                "Edited title", null, null, null, Priority.CRITICAL, 8,
                List.of(new AcceptSuggestionCommand.Criterion("edited label",
                        "edited given", "edited when", "edited then"))));

        var captor = ArgumentCaptor.forClass(UserStory.class);
        verify(storyRepo).save(captor.capture());
        UserStory story = captor.getValue();
        assertThat(story.getTitle()).isEqualTo("Edited title");
        assertThat(story.getRole()).isEqualTo("user"); // untouched draft field falls through
        assertThat(story.getPriority()).isEqualTo(Priority.CRITICAL);
        assertThat(story.getStoryPoints()).isEqualTo(8);
        assertThat(story.getAcceptanceCriteria()).hasSize(1);
        var criterion = story.getAcceptanceCriteria().getFirst();
        assertThat(criterion.getScenario()).isEqualTo("edited label");
        assertThat(criterion.getGiven()).isEqualTo("edited given");
        assertThat(criterion.getWhen()).isEqualTo("edited when");
        assertThat(criterion.getThen()).isEqualTo("edited then");
    }

    private static AcceptSuggestionCommand accept(Suggestion s) {
        return new AcceptSuggestionCommand(s.getSessionId(), s.getId(),
                null, null, null, null, null, null, null);
    }
}
