package com.kntro.reqsai.discovery.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Domain: story text used to compare the proposed and the final story")
class StoryTextTest {

    private static UserStory story() {
        return new UserStory(UUID.randomUUID(), UUID.randomUUID(), " Reservar mesa ", "comensal", "reservar una mesa",
                "no hacer fila", Priority.HIGH, 3);
    }

    @Test
    @DisplayName("lists title, role, action and benefit, one per line, trimmed")
    void narrative() {
        assertThat(StoryText.of(story())).isEqualTo("Reservar mesa\ncomensal\nreservar una mesa\nno hacer fila");
    }

    @Test
    @DisplayName("appends each criterion as 'scenario: given | when | then'")
    void criteria() {
        UserStory story = story();
        story.addAcceptanceCriterion("Confirmada", "un horario", "reserva", "queda confirmada");
        story.addAcceptanceCriterion(null, "sin horario", "reserva", "ve un error");

        assertThat(StoryText.of(story)).isEqualTo("Reservar mesa\ncomensal\nreservar una mesa\nno hacer fila\n"
                + "Confirmada: un horario | reserva | queda confirmada\nsin horario | reserva | ve un error");
    }

    @Test
    @DisplayName("reordering the criteria does not change the text")
    void criteriaOrderIsIrrelevant() {
        UserStory first = story();
        first.addAcceptanceCriterion(null, "a", "b", "c");
        first.addAcceptanceCriterion(null, "x", "y", "z");
        UserStory second = story();
        second.addAcceptanceCriterion(null, "x", "y", "z");
        second.addAcceptanceCriterion(null, "a", "b", "c");

        assertThat(StoryText.of(first)).isEqualTo(StoryText.of(second));
    }

    @Test
    @DisplayName("a suggestion's draft is composed the same way as the story it becomes")
    void suggestionMatchesStory() {
        Suggestion suggestion = Suggestion.newStory(UUID.randomUUID(), UUID.randomUUID(), "Reservar mesa", "comensal",
                "reservar una mesa", "no hacer fila", Priority.HIGH, 3,
                List.of(new Suggestion.DraftCriterion("Confirmada", "un horario", "reserva", "queda confirmada")));
        UserStory story = story();
        story.addAcceptanceCriterion("Confirmada", "un horario", "reserva", "queda confirmada");

        assertThat(StoryText.of(suggestion)).isEqualTo(StoryText.of(story));
    }

    @Test
    @DisplayName("missing draft fields read as empty lines")
    void missingFields() {
        assertThat(StoryText.compose(null, null, null, null, List.of())).isEqualTo("\n\n\n");
    }
}
