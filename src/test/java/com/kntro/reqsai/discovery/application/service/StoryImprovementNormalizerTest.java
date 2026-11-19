package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.StoryDraft;
import com.kntro.reqsai.discovery.application.port.StoryImprovementResult;
import com.kntro.reqsai.discovery.application.port.StoryImprovementResult.PendingValue;
import com.kntro.reqsai.discovery.application.port.UnparseableGenerationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Application: story improvement normalizer and pending values")
class StoryImprovementNormalizerTest {

    private static final String TOKEN = "[[tiempo máximo de respuesta]]";

    private static StoryDraft draft(String title, String then) {
        return new StoryDraft(title, "analista", "exportar el backlog", "compartirlo con el cliente",
                List.of(new StoryDraft.CriterionDraft(" camino feliz ", "hay historias", "exporta", then)));
    }

    private static StoryImprovementResult result(StoryDraft draft, List<PendingValue> pending, String... fixes) {
        return new StoryImprovementResult(draft, pending, List.of(fixes));
    }

    @Test
    @DisplayName("tokens are found once each, in order of appearance, in every field of the story")
    void finds_tokens_in_every_field() {
        StoryDraft draft = new StoryDraft("Exportar [[formato]]", "[[rol]]", "exportar", "compartir [[formato]]",
                List.of(new StoryDraft.CriterionDraft("[[escenario]]", "dado [[a]]", "cuando", "entonces [[b]] y [[a]]")));

        assertThat(PendingValues.in(draft))
                .containsExactly("[[formato]]", "[[rol]]", "[[escenario]]", "[[a]]", "[[b]]");
        assertThat(PendingValues.in("sin nada [ ] ni [x] ni [[ sin cerrar")).isEmpty();
        assertThat(PendingValues.in("vacío [[]] y [[ ]]")).containsExactly("[[]]", "[[ ]]");
    }

    @Test
    @DisplayName("a clean proposal is trimmed and its tokens are listed in order with their hints")
    void normalizes_a_valid_proposal() {
        StoryImprovementResult out = StoryImprovementNormalizer.normalize(result(
                draft("  Exportar el backlog ", "responde en menos de " + TOKEN + " segundos"),
                List.of(new PendingValue(TOKEN, "¿En cuántos segundos debe responder?")),
                "Se reemplazó «rápido» por un valor pendiente", "  ", "Se reemplazó «rápido» por un valor pendiente"));

        assertThat(out.proposal().title()).isEqualTo("Exportar el backlog");
        assertThat(out.proposal().acceptanceCriteria().getFirst().scenario()).isEqualTo("camino feliz");
        assertThat(out.pendingValues()).containsExactly(new PendingValue(TOKEN, "¿En cuántos segundos debe responder?"));
        assertThat(out.fixes()).containsExactly("Se reemplazó «rápido» por un valor pendiente");
    }

    @Test
    @DisplayName("a declared token that is not in the text is dropped; one written without brackets is wrapped")
    void normalizes_declarations() {
        StoryImprovementResult out = StoryImprovementNormalizer.normalize(result(
                draft("Exportar", "responde en menos de " + TOKEN + " segundos"),
                List.of(new PendingValue("tiempo máximo de respuesta", "¿Cuántos segundos?"),
                        new PendingValue("[[no está en el texto]]", "¿?"),
                        new PendingValue("[[x]] roto]]", "¿?"))));

        assertThat(out.pendingValues()).containsExactly(new PendingValue(TOKEN, "¿Cuántos segundos?"));
    }

    @Test
    @DisplayName("a declaration without a hint falls back to the words inside the brackets")
    void hint_falls_back_to_the_token_text() {
        StoryImprovementResult out = StoryImprovementNormalizer.normalize(result(
                draft("Exportar", "responde en menos de " + TOKEN + " segundos"),
                List.of(new PendingValue(TOKEN, " "))));

        assertThat(out.pendingValues()).containsExactly(new PendingValue(TOKEN, "tiempo máximo de respuesta"));
    }

    @Test
    @DisplayName("a token written in the text but not declared is rejected, so the analyst is never left guessing")
    void rejects_undeclared_tokens() {
        assertThatThrownBy(() -> StoryImprovementNormalizer.normalize(result(
                draft("Exportar", "responde en menos de " + TOKEN + " segundos"), List.of())))
                .isInstanceOf(UnparseableGenerationException.class)
                .hasMessageContaining("does not declare").hasMessageContaining(TOKEN);
    }

    @Test
    @DisplayName("texts over the story limits, blank required texts and too many criteria are rejected, never cut")
    void rejects_texts_over_the_limits() {
        assertThatThrownBy(() -> StoryImprovementNormalizer.normalize(
                result(draft("t".repeat(201), "muestra un mensaje"), List.of())))
                .isInstanceOf(UnparseableGenerationException.class).hasMessageContaining("'title'");
        assertThatThrownBy(() -> StoryImprovementNormalizer.normalize(
                result(draft("Exportar", "m".repeat(1001)), List.of())))
                .isInstanceOf(UnparseableGenerationException.class).hasMessageContaining("'then'");
        assertThatThrownBy(() -> StoryImprovementNormalizer.normalize(
                result(draft("  ", "muestra un mensaje"), List.of())))
                .isInstanceOf(UnparseableGenerationException.class).hasMessageContaining("'title' is empty");

        List<StoryDraft.CriterionDraft> many = new ArrayList<>();
        for (int i = 0; i <= StoryImprovementNormalizer.MAX_CRITERIA; i++) {
            many.add(new StoryDraft.CriterionDraft(null, "dado", "cuando", "entonces muestra"));
        }
        assertThatThrownBy(() -> StoryImprovementNormalizer.normalize(
                result(new StoryDraft("Exportar", "analista", "exportar", "compartir", many), List.of())))
                .isInstanceOf(UnparseableGenerationException.class).hasMessageContaining("acceptance criteria");
    }

    @Test
    @DisplayName("the fixes are limited in number and length")
    void limits_the_fixes() {
        String[] fixes = new String[15];
        for (int i = 0; i < fixes.length; i++) {
            fixes[i] = "arreglo " + i + " " + "x".repeat(i == 0 ? 400 : 1);
        }

        StoryImprovementResult out = StoryImprovementNormalizer.normalize(
                result(draft("Exportar", "muestra un mensaje"), List.of(), fixes));

        assertThat(out.fixes()).hasSize(StoryImprovementNormalizer.MAX_FIXES);
        assertThat(out.fixes().getFirst()).hasSize(StoryImprovementNormalizer.FIX_MAX).endsWith("…");
    }
}
