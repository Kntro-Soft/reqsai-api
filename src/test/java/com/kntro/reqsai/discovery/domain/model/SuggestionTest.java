package com.kntro.reqsai.discovery.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link Suggestion}, focused on the draft acceptance-criteria codec that carries the
 * LLM's proposed Given/When/Then criteria through the review gate as newline/tab-delimited TEXT.
 */
@DisplayName("Domain: Suggestion draft criteria")
class SuggestionTest {

    private final UUID sessionId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    private Suggestion newStoryWith(List<Suggestion.DraftCriterion> criteria) {
        return Suggestion.newStory(sessionId, projectId,
                "Iniciar sesión", "usuario", "iniciar sesión", "acceder", Priority.HIGH, 3, criteria);
    }

    @Test
    @DisplayName("should preserve structured criteria in order, with null scenario when absent")
    void should_round_trip_criteria() {
        Suggestion s = newStoryWith(List.of(
                new Suggestion.DraftCriterion("Válido", "tiene cuenta", "ingresa bien", "accede"),
                new Suggestion.DraftCriterion(null, "clave mala", "intenta", "ve error")));

        List<Suggestion.DraftCriterion> decoded = s.getDraftAcceptanceCriteria();

        assertThat(decoded).hasSize(2);
        assertThat(decoded.getFirst().scenario()).isEqualTo("Válido");
        assertThat(decoded.getFirst().given()).isEqualTo("tiene cuenta");
        assertThat(decoded.getLast().scenario()).isNull();
        assertThat(decoded.getLast().then()).isEqualTo("ve error");
    }

    @Test
    @DisplayName("should drop a criterion missing given/when/then rather than encode a broken row")
    void should_drop_incomplete_criterion() {
        Suggestion s = newStoryWith(List.of(
                new Suggestion.DraftCriterion("ok", "g", "w", "t"),
                new Suggestion.DraftCriterion("incompleta", "", "w", "t")));

        assertThat(s.getDraftAcceptanceCriteria()).hasSize(1);
        assertThat(s.getDraftAcceptanceCriteria().getFirst().scenario()).isEqualTo("ok");
    }

    @Test
    @DisplayName("should strip surrounding whitespace and normalize a blank scenario to null")
    void should_strip_and_normalize_scenario() {
        Suggestion s = newStoryWith(List.of(
                new Suggestion.DraftCriterion("   ", "  tiene cuenta  ", "ingresa", "accede")));

        Suggestion.DraftCriterion c = s.getDraftAcceptanceCriteria().getFirst();
        assertThat(c.scenario()).isNull();
        assertThat(c.given()).isEqualTo("tiene cuenta");
    }

    @Test
    @DisplayName("should carry no criteria for the no-criteria factory")
    void should_be_empty_without_criteria() {
        Suggestion s = Suggestion.newStory(sessionId, projectId,
                "T", "r", "a", "b", Priority.LOW, 1);

        assertThat(s.getDraftAcceptanceCriteria()).isEmpty();
    }
}
