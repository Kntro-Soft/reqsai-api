package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.AcceptSuggestionCommand;
import com.kntro.reqsai.discovery.application.port.SuggestionRepository;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.StoryText;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
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
 * What the accept handler freezes as the text the AI proposed (the baseline of the quality indicators) and when it
 * leaves it alone.
 */
@DisplayName("Application: Accept Suggestion keeps the text the AI proposed")
@ExtendWith(MockitoExtension.class)
class AcceptSuggestionGeneratedBaselineTest {

    @Mock
    private SuggestionRepository suggestions;
    @Mock
    private UserStoryRepository storyRepo;
    @Mock
    private EmbeddingPort embeddingPort;
    @InjectMocks
    private AcceptSuggestionCommandHandler handler;

    private static Suggestion newStory() {
        return Suggestion.newStory(UUID.randomUUID(), UUID.randomUUID(), "Reservar mesa", "comensal",
                "reservar una mesa", "no hacer fila", Priority.HIGH, 3,
                List.of(new Suggestion.DraftCriterion(null, "un horario", "reserva", "queda confirmada")));
    }

    private UserStory accept(Suggestion suggestion, AcceptSuggestionCommand command) {
        when(suggestions.findByIdAndSessionIdForUpdate(any(), any())).thenReturn(Optional.of(suggestion));
        when(embeddingPort.isAvailable()).thenReturn(false);
        when(storyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        handler.handle(command);
        var captor = ArgumentCaptor.forClass(UserStory.class);
        verify(storyRepo).save(captor.capture());
        return captor.getValue();
    }

    private static AcceptSuggestionCommand plain(Suggestion s, boolean approve) {
        return new AcceptSuggestionCommand(s.getSessionId(), s.getId(), null, null, null, null, null, null, null,
                approve);
    }

    @Test
    @DisplayName("a story accepted as proposed has the proposal as baseline, equal to what it says")
    void acceptedAsProposed() {
        Suggestion suggestion = newStory();
        String proposed = StoryText.of(suggestion);

        UserStory story = accept(suggestion, plain(suggestion, true));

        assertThat(story.getGeneratedText()).isEqualTo(proposed);
        assertThat(story.currentText()).isEqualTo(proposed);
        assertThat(story.getReviewedAt()).isNotNull();
    }

    @Test
    @DisplayName("analyst edits before accepting do not move the baseline: it stays what the AI proposed")
    void editsKeepTheProposal() {
        Suggestion suggestion = newStory();
        String proposed = StoryText.of(suggestion);

        UserStory story = accept(suggestion, new AcceptSuggestionCommand(suggestion.getSessionId(),
                suggestion.getId(), "Otro título", null, null, null, null, null,
                List.of(new AcceptSuggestionCommand.Criterion(null, "otro dado", "otro cuando", "otro entonces")),
                true));

        assertThat(story.getGeneratedText()).isEqualTo(proposed);
        assertThat(story.currentText()).isNotEqualTo(proposed).startsWith("Otro título");
    }

    @Test
    @DisplayName("saving as draft records no review time")
    void draftHasNoReviewTime() {
        Suggestion suggestion = newStory();

        UserStory story = accept(suggestion, plain(suggestion, false));

        assertThat(story.getReviewedAt()).isNull();
        assertThat(story.getGeneratedText()).isNotNull();
    }

    @Test
    @DisplayName("an update taken as proposed re-bases the baseline of an AI story, so the AI's own change is not a rewrite")
    void updateAsProposedRebases() {
        UUID projectId = UUID.randomUUID();
        UserStory target = new UserStory(UUID.randomUUID(), projectId, "Reservar mesa", "comensal",
                "reservar una mesa", "no hacer fila", Priority.HIGH, 3);
        target.captureGeneratedText();
        Suggestion suggestion = Suggestion.updateStory(UUID.randomUUID(), projectId, "Reservar mesa", "comensal",
                "reservar una mesa", "no hacer fila", Priority.HIGH, 3, target.getId(),
                List.of(new Suggestion.DraftCriterion(null, "sin horario", "reserva", "ve un error")));
        when(suggestions.findByIdAndSessionIdForUpdate(any(), any())).thenReturn(Optional.of(suggestion));
        when(storyRepo.findById(target.getId())).thenReturn(Optional.of(target));
        when(embeddingPort.isAvailable()).thenReturn(false);
        when(storyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler.handle(plain(suggestion, false));

        assertThat(target.getGeneratedText()).isEqualTo(target.currentText()).contains("sin horario");
    }

    @Test
    @DisplayName("an update with analyst edits leaves the baseline alone, so the edit counts as a change")
    void updateWithEditsKeepsBaseline() {
        UUID projectId = UUID.randomUUID();
        UserStory target = new UserStory(UUID.randomUUID(), projectId, "Reservar mesa", "comensal",
                "reservar una mesa", "no hacer fila", Priority.HIGH, 3);
        target.captureGeneratedText();
        String baseline = target.getGeneratedText();
        Suggestion suggestion = Suggestion.updateStory(UUID.randomUUID(), projectId, "Reservar mesa", "comensal",
                "reservar una mesa", "no hacer fila", Priority.HIGH, 3, target.getId(), List.of());
        when(suggestions.findByIdAndSessionIdForUpdate(any(), any())).thenReturn(Optional.of(suggestion));
        when(storyRepo.findById(target.getId())).thenReturn(Optional.of(target));
        when(embeddingPort.isAvailable()).thenReturn(false);
        when(storyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler.handle(new AcceptSuggestionCommand(suggestion.getSessionId(), suggestion.getId(), "Reservar sala",
                null, null, null, null, null, null));

        assertThat(target.getGeneratedText()).isEqualTo(baseline);
        assertThat(target.currentText()).isNotEqualTo(baseline);
    }

    @Test
    @DisplayName("an update of a story that did not come from the AI does not invent a baseline")
    void updateOfManualStoryHasNoBaseline() {
        UUID projectId = UUID.randomUUID();
        UserStory target = new UserStory(projectId, "Reservar mesa", "comensal", "reservar una mesa",
                "no hacer fila", Priority.HIGH, 3);
        Suggestion suggestion = Suggestion.updateStory(UUID.randomUUID(), projectId, "Reservar mesa", "comensal",
                "reservar una mesa", "no hacer fila", Priority.HIGH, 3, target.getId(), List.of());
        when(suggestions.findByIdAndSessionIdForUpdate(any(), any())).thenReturn(Optional.of(suggestion));
        when(storyRepo.findById(target.getId())).thenReturn(Optional.of(target));
        when(embeddingPort.isAvailable()).thenReturn(false);
        when(storyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler.handle(plain(suggestion, false));

        assertThat(target.getGeneratedText()).isNull();
    }

    @Test
    @DisplayName("an edge case taken as proposed re-bases the baseline with the criterion it adds")
    void edgeCaseAsProposedRebases() {
        UUID projectId = UUID.randomUUID();
        UserStory target = new UserStory(UUID.randomUUID(), projectId, "Reservar mesa", "comensal",
                "reservar una mesa", "no hacer fila", Priority.HIGH, 3);
        target.captureGeneratedText();
        Suggestion suggestion = Suggestion.edgeCase(UUID.randomUUID(), projectId, "Sin cupo", "comensal",
                "reservar", "no hacer fila", Priority.MEDIUM, 2, "reservas", target.getId(),
                new Suggestion.DraftCriterion("Sin cupo", "no hay mesas", "reserva", "queda en lista de espera"));
        when(suggestions.findByIdAndSessionIdForUpdate(any(), any())).thenReturn(Optional.of(suggestion));
        when(storyRepo.findById(target.getId())).thenReturn(Optional.of(target));
        when(storyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler.handle(plain(suggestion, false));

        assertThat(target.getGeneratedText()).isEqualTo(target.currentText()).contains("lista de espera");
    }
}
