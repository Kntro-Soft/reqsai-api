package com.kntro.reqsai.discovery.infrastructure.quality;

import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.QualityCharacteristic;
import com.kntro.reqsai.discovery.domain.model.QualityFinding;
import com.kntro.reqsai.discovery.domain.model.StoryQualityAssessment;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The scoring rules of the rule-based evaluator, characteristic by characteristic (US68). */
@Tag("unit")
@DisplayName("RuleBasedStoryQualityEvaluator")
class RuleBasedStoryQualityEvaluatorTest {

    private final RuleBasedStoryQualityEvaluator evaluator = new RuleBasedStoryQualityEvaluator();

    private static final String GOOD_THEN = "el sistema descarga un archivo CSV con todas las historias";

    private static UserStory story(String role, String action, String benefit, Integer points) {
        return new UserStory(UUID.randomUUID(), "Exportar el backlog", role, action, benefit, Priority.MEDIUM, points);
    }

    /** A story that satisfies every rule, with one verifiable criterion. */
    private static UserStory goodStory() {
        UserStory story = story("analista de cumplimiento", "exportar el backlog a CSV",
                "compartirlo con el cliente sin reescribirlo", 3);
        story.addAcceptanceCriterion("camino feliz", "hay historias aprobadas", "exporto el backlog", GOOD_THEN);
        return story;
    }

    private QualityFinding finding(UserStory story, QualityCharacteristic characteristic) {
        List<QualityFinding> findings = evaluator.evaluate(story);
        return findings.stream().filter(f -> f.characteristic() == characteristic).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("returns exactly one finding per characteristic, in a stable order")
    void one_finding_per_characteristic() {
        assertThat(evaluator.evaluate(goodStory())).extracting(QualityFinding::characteristic)
                .containsExactly(QualityCharacteristic.AMBIGUITY, QualityCharacteristic.ATOMICITY,
                        QualityCharacteristic.VERIFIABILITY, QualityCharacteristic.COMPLETENESS);
    }

    @Test
    @DisplayName("happy path: a complete story with criteria scores 100 and lists every characteristic as met")
    void happy_path() {
        List<QualityFinding> findings = evaluator.evaluate(goodStory());

        assertThat(findings).allSatisfy(f -> {
            assertThat(f.met()).as(f.characteristic() + " " + f.observation()).isTrue();
            assertThat(f.observation()).isNotBlank();
            assertThat(f.suggestion()).isNull();
        });
        assertThat(StoryQualityAssessment.overallScore(findings)).isEqualTo(100);
    }

    @Test
    @DisplayName("is deterministic: the same story gives the same findings")
    void deterministic() {
        UserStory story = story("usuario", "ver y editar pedidos rápido", "mejor control, etc.", 13);
        assertThat(evaluator.evaluate(story)).isEqualTo(evaluator.evaluate(story));
    }

    @Nested
    @DisplayName("ambiguity")
    class Ambiguity {

        @Test
        @DisplayName("unhappy path: flags vague terms and suggests measurable replacements")
        void flags_vague_terms() {
            UserStory story = story("analista", "exportar el backlog de forma rápida y fácil",
                    "compartirlo con el cliente", 3);
            story.addAcceptanceCriterion(null, "hay historias", "exporto", GOOD_THEN);

            QualityFinding finding = finding(story, QualityCharacteristic.AMBIGUITY);

            assertThat(finding.met()).isFalse();
            assertThat(finding.score()).isEqualTo(50);
            assertThat(finding.observation()).contains("«rápido»").contains("«fácil»");
            assertThat(finding.suggestion()).contains("medible");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"rápido", "Rápidamente", "FÁCIL", "adecuado", "apropiada", "óptimo", "eficiente",
                "amigable", "intuitivo", "flexible", "robusto", "mejor", "suficiente", "razonable", "normalmente",
                "a veces", "algunos", "varios", "muchos", "pocos", "lo antes posible", "tan pronto como sea posible",
                "cuando sea necesario", "si es posible", "según corresponda", "etc.", "etc", "entre otros", "y/o",
                "y así sucesivamente", "correctamente", "bueno"})
        @DisplayName("detects the Spanish term")
        void spanish_terms(String term) {
            assertThat(RuleBasedStoryQualityEvaluator.ambiguousTerms("El sistema debe responder " + term + " siempre"))
                    .as(term).isNotEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"fast", "Quickly", "easy", "user-friendly", "user friendly", "intuitive", "flexible",
                "robust", "efficient", "optimal", "adequate", "appropriate", "seamless", "good", "better", "sufficient",
                "reasonable", "properly", "usually", "several", "as soon as possible", "ASAP", "as needed",
                "if possible", "and so on", "etc.", "and/or"})
        @DisplayName("detects the English term")
        void english_terms(String term) {
            assertThat(RuleBasedStoryQualityEvaluator.ambiguousTerms("The system must respond " + term + " always"))
                    .as(term).isNotEmpty();
        }

        @Test
        @DisplayName("detects the open-ended ellipsis")
        void ellipsis() {
            assertThat(RuleBasedStoryQualityEvaluator.ambiguousTerms("filtrar por fecha, estado, prioridad..."))
                    .containsExactly("...");
            assertThat(RuleBasedStoryQualityEvaluator.ambiguousTerms("filtrar por fecha…")).containsExactly("...");
        }

        @Test
        @DisplayName("matches whole words only, so «rapidez» or «facilitar» are not flagged")
        void whole_words_only() {
            assertThat(RuleBasedStoryQualityEvaluator.ambiguousTerms(
                    "facilitar la rapidez de la carga y el fastidio del buzón")).isEmpty();
            assertThat(RuleBasedStoryQualityEvaluator.ambiguousTerms("el etcetera")).isNotEmpty();
            assertThat(RuleBasedStoryQualityEvaluator.ambiguousTerms("la comida del goodwill")).isEmpty();
        }

        @Test
        @DisplayName("ignores accents and case when matching")
        void accent_and_case_insensitive() {
            assertThat(RuleBasedStoryQualityEvaluator.ambiguousTerms("RAPIDO"))
                    .isEqualTo(RuleBasedStoryQualityEvaluator.ambiguousTerms("rápido"))
                    .containsExactly("rápido");
        }

        @Test
        @DisplayName("counts each distinct term once and scores 25 less per term, down to 0")
        void penalty_per_distinct_term() {
            UserStory story = story("analista", "exportar el backlog rápido, rápido, rápidamente", "tenerlo", 3);
            assertThat(finding(story, QualityCharacteristic.AMBIGUITY).score()).isEqualTo(75);

            UserStory many = story("analista", "exportar el backlog rápido y fácil, adecuado, óptimo y flexible",
                    "mejor control, etc.", 3);
            QualityFinding finding = finding(many, QualityCharacteristic.AMBIGUITY);
            assertThat(finding.score()).isZero();
            assertThat(finding.observation()).contains("«rápido»");
        }

        @Test
        @DisplayName("also reads the title, the benefit and the acceptance criteria")
        void reads_every_text() {
            UserStory story = story("analista", "exportar el backlog a CSV", "tener una vista amigable", 3);
            story.addAcceptanceCriterion("escenario", "hay historias", "exporto", "el sistema muestra un resultado adecuado");

            assertThat(finding(story, QualityCharacteristic.AMBIGUITY).observation())
                    .contains("«amigable»").contains("«adecuado»");
        }

        @Test
        @DisplayName("lists only the first terms when there are many")
        void lists_first_terms() {
            UserStory story = story("analista", "exportar rápido, fácil, adecuado, óptimo, flexible, robusto, eficiente",
                    "tener mejor control", 3);

            QualityFinding finding = finding(story, QualityCharacteristic.AMBIGUITY);

            assertThat(finding.observation().split("«", -1)).hasSize(7);
        }
    }

    @Nested
    @DisplayName("atomicity")
    class Atomicity {

        @Test
        @DisplayName("a single action is atomic")
        void single_action() {
            assertThat(finding(goodStory(), QualityCharacteristic.ATOMICITY).met()).isTrue();
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"registrar y consultar pedidos", "crear, editar y eliminar proyectos",
                "ver los pedidos; exportarlos a CSV", "poder crear una cuenta e iniciar sesión",
                "create and edit orders", "to search, filter and sort the backlog"})
        @DisplayName("flags several actions joined in one story")
        void multiple_actions(String action) {
            UserStory story = story("analista", action, "trabajar sin reescribir los datos", 3);

            QualityFinding finding = finding(story, QualityCharacteristic.ATOMICITY);

            assertThat(finding.met()).as(action).isFalse();
            assertThat(finding.suggestion()).isNotBlank();
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"buscar productos por nombre o categoría", "ver el historial de pedidos y su estado",
                "consultar pedidos, clientes y productos", "exportar el backlog a CSV", "ver pedidos o clientes",
                "search products by name and category"})
        @DisplayName("does not flag objects joined by a conjunction as several actions")
        void single_action_with_joined_objects(String action) {
            UserStory story = story("analista", action, "trabajar sin reescribir los datos", 3);

            assertThat(finding(story, QualityCharacteristic.ATOMICITY).met()).as(action).isTrue();
        }

        @Test
        @DisplayName("each extra action costs 30 points")
        void penalty_per_extra_action() {
            assertThat(finding(story("analista", "crear y editar pedidos", "controlarlos mejor", 3),
                    QualityCharacteristic.ATOMICITY).score()).isEqualTo(70);
            assertThat(finding(story("analista", "crear, editar y eliminar pedidos", "controlarlos mejor", 3),
                    QualityCharacteristic.ATOMICITY).score()).isEqualTo(40);
        }

        @Test
        @DisplayName("the observation names the detected actions in Spanish")
        void names_actions() {
            QualityFinding finding = finding(story("analista", "registrar y consultar pedidos", "controlarlos", 3),
                    QualityCharacteristic.ATOMICITY);

            assertThat(finding.observation()).contains("varias acciones").contains("registrar").contains("consultar");
            assertThat(finding.suggestion()).contains("una historia por acción");
        }

        @Test
        @DisplayName("an over-long action is flagged")
        void long_action() {
            String action = "exportar " + "el backlog completo ".repeat(10) + "a CSV";
            QualityFinding finding = finding(story("analista", action, "compartirlo con el cliente", 3),
                    QualityCharacteristic.ATOMICITY);

            assertThat(finding.score()).isEqualTo(85);
            assertThat(finding.observation()).contains("demasiado extensa");
        }

        @Test
        @DisplayName("a story estimated at 13 points or more is too large; 8 is fine")
        void large_estimate() {
            assertThat(finding(story("analista", "exportar el backlog", "compartirlo", 8),
                    QualityCharacteristic.ATOMICITY).met()).isTrue();
            QualityFinding finding = finding(story("analista", "exportar el backlog", "compartirlo", 13),
                    QualityCharacteristic.ATOMICITY);
            assertThat(finding.score()).isEqualTo(80);
            assertThat(finding.observation()).contains("13 puntos");
            assertThat(finding(story("analista", "exportar el backlog", "compartirlo", null),
                    QualityCharacteristic.ATOMICITY).met()).isTrue();
        }

        @Test
        @DisplayName("more than 8 acceptance criteria suggest more than one capability")
        void too_many_criteria() {
            UserStory story = story("analista", "exportar el backlog", "compartirlo con el cliente", 3);
            for (int i = 0; i < 8; i++) {
                story.addAcceptanceCriterion(null, "caso " + i, "exporto", GOOD_THEN);
            }
            assertThat(finding(story, QualityCharacteristic.ATOMICITY).met()).isTrue();

            story.addAcceptanceCriterion(null, "caso 9", "exporto", GOOD_THEN);
            QualityFinding finding = finding(story, QualityCharacteristic.ATOMICITY);
            assertThat(finding.score()).isEqualTo(85);
            assertThat(finding.observation()).contains("9 criterios");
        }

        @Test
        @DisplayName("penalties add up and never go below 0")
        void penalties_add_up() {
            String action = "crear, editar, eliminar, exportar y compartir " + "pedidos del cliente ".repeat(8);
            UserStory story = story("analista", action, "controlarlos", 21);

            QualityFinding finding = finding(story, QualityCharacteristic.ATOMICITY);

            assertThat(finding.score()).isZero();
            assertThat(finding.observation()).contains("varias acciones").contains("demasiado extensa")
                    .contains("21 puntos");
        }

        @Test
        @DisplayName("actionVerbs lists the verb of each joined action")
        void action_verbs() {
            assertThat(RuleBasedStoryQualityEvaluator.actionVerbs("crear, editar y eliminar proyectos"))
                    .containsExactly("crear", "editar", "eliminar");
            assertThat(RuleBasedStoryQualityEvaluator.actionVerbs("poder registrarse y consultar su saldo"))
                    .containsExactly("registrarse", "consultar");
            assertThat(RuleBasedStoryQualityEvaluator.actionVerbs("be able to create and edit orders"))
                    .containsExactly("create", "edit");
            assertThat(RuleBasedStoryQualityEvaluator.actionVerbs("el lugar de trabajo")).isEmpty();
        }
    }

    @Nested
    @DisplayName("verifiability")
    class Verifiability {

        @Test
        @DisplayName("unhappy path: a story without acceptance criteria scores 0 and asks for them")
        void without_criteria() {
            UserStory story = story("analista", "exportar el backlog a CSV", "compartirlo con el cliente", 3);

            QualityFinding finding = finding(story, QualityCharacteristic.VERIFIABILITY);

            assertThat(finding.met()).isFalse();
            assertThat(finding.score()).isZero();
            assertThat(finding.observation()).contains("no tiene criterios de aceptación");
            assertThat(finding.suggestion()).contains("Dado/Cuando/Entonces");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"el sistema muestra un mensaje de confirmación", "responde en menos de 2 segundos",
                "se envía un correo al administrador", "el pedido queda en estado aprobado",
                "the system displays a confirmation message", "the file is saved with 10 rows",
                "el usuario puede ver el detalle", "se genera un archivo con todas las historias"})
        @DisplayName("a «Then» with an observable or measurable outcome is verifiable")
        void verifiable(String then) {
            UserStory story = story("analista", "exportar el backlog", "compartirlo con el cliente", 3);
            story.addAcceptanceCriterion(null, "hay historias", "exporto", then);

            assertThat(finding(story, QualityCharacteristic.VERIFIABILITY).met()).as(then).isTrue();
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"funciona bien", "el sistema es rápido", "todo sale correcto", "it works well",
                "ok", "el proceso termina satisfactoriamente"})
        @DisplayName("unhappy path: a «Then» without an observable or measurable outcome is not verifiable")
        void not_verifiable(String then) {
            UserStory story = story("analista", "exportar el backlog", "compartirlo con el cliente", 3);
            story.addAcceptanceCriterion(null, "hay historias", "exporto", then);

            QualityFinding finding = finding(story, QualityCharacteristic.VERIFIABILITY);

            assertThat(finding.met()).as(then).isFalse();
            assertThat(finding.observation()).contains("criterio 1").contains(then);
            assertThat(finding.suggestion()).contains("comprobable");
        }

        @Test
        @DisplayName("with mixed criteria the score is the share that can be checked, capped at 75")
        void partial() {
            UserStory story = story("analista", "exportar el backlog", "compartirlo con el cliente", 3);
            story.addAcceptanceCriterion(null, "a", "b", GOOD_THEN);
            story.addAcceptanceCriterion(null, "a", "b", "funciona bien");

            QualityFinding finding = finding(story, QualityCharacteristic.VERIFIABILITY);

            assertThat(finding.score()).isEqualTo(50);
            assertThat(finding.observation()).startsWith("1 de 2 criterios").contains("criterio 2");

            story.addAcceptanceCriterion(null, "a", "b", GOOD_THEN);
            story.addAcceptanceCriterion(null, "a", "b", GOOD_THEN);
            story.addAcceptanceCriterion(null, "a", "b", GOOD_THEN);
            assertThat(finding(story, QualityCharacteristic.VERIFIABILITY).score()).isEqualTo(75);
        }

        @Test
        @DisplayName("quotes a long «Then» truncated")
        void truncates_long_then() {
            UserStory story = story("analista", "exportar el backlog", "compartirlo con el cliente", 3);
            story.addAcceptanceCriterion(null, "a", "b", "todo funciona " + "muy bien ".repeat(30));

            assertThat(finding(story, QualityCharacteristic.VERIFIABILITY).observation()).contains("...»");
        }

        @Test
        @DisplayName("a story whose criteria all state an outcome is met")
        void all_verifiable() {
            assertThat(finding(goodStory(), QualityCharacteristic.VERIFIABILITY).observation())
                    .contains("1 criterios de aceptación declaran");
        }
    }

    @Nested
    @DisplayName("completeness")
    class Completeness {

        @Test
        @DisplayName("a story with role, action, benefit and criteria is complete")
        void complete() {
            assertThat(finding(goodStory(), QualityCharacteristic.COMPLETENESS).met()).isTrue();
        }

        @Test
        @DisplayName("unhappy path: a story without acceptance criteria is incomplete")
        void without_criteria() {
            UserStory story = story("analista", "exportar el backlog a CSV", "compartirlo con el cliente", 3);

            QualityFinding finding = finding(story, QualityCharacteristic.COMPLETENESS);

            assertThat(finding.score()).isEqualTo(75);
            assertThat(finding.observation()).contains("criterios de aceptación");
            assertThat(finding.suggestion()).contains("criterios de aceptación");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"usuario", "el usuario", "user", "persona", "alguien", "sistema"})
        @DisplayName("a generic role is flagged")
        void generic_role(String role) {
            UserStory story = story(role, "exportar el backlog a CSV", "compartirlo con el cliente", 3);
            story.addAcceptanceCriterion(null, "a", "b", GOOD_THEN);

            QualityFinding finding = finding(story, QualityCharacteristic.COMPLETENESS);

            assertThat(finding.score()).isEqualTo(90);
            assertThat(finding.observation()).contains("genérico");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"TBD", "n/a", "por definir", "?", "x", "pendiente"})
        @DisplayName("a placeholder role counts as missing")
        void placeholder_role(String role) {
            UserStory story = story(role, "exportar el backlog a CSV", "compartirlo con el cliente", 3);
            story.addAcceptanceCriterion(null, "a", "b", GOOD_THEN);

            QualityFinding finding = finding(story, QualityCharacteristic.COMPLETENESS);

            assertThat(finding.score()).isEqualTo(75);
            assertThat(finding.observation()).contains("Falta el rol");
        }

        @Test
        @DisplayName("a short but real role such as PM is accepted")
        void short_role() {
            UserStory story = story("PM", "exportar el backlog a CSV", "compartirlo con el cliente", 3);
            story.addAcceptanceCriterion(null, "a", "b", GOOD_THEN);

            assertThat(finding(story, QualityCharacteristic.COMPLETENESS).met()).isTrue();
        }

        @Test
        @DisplayName("a one-word action or a placeholder action is flagged")
        void thin_action() {
            UserStory one = story("analista", "exportar", "compartirlo con el cliente", 3);
            one.addAcceptanceCriterion(null, "a", "b", GOOD_THEN);
            UserStory placeholder = story("analista", "TBD", "compartirlo con el cliente", 3);
            placeholder.addAcceptanceCriterion(null, "a", "b", GOOD_THEN);

            assertThat(finding(one, QualityCharacteristic.COMPLETENESS).observation()).contains("acción");
            assertThat(finding(placeholder, QualityCharacteristic.COMPLETENESS).score()).isEqualTo(75);
        }

        @Test
        @DisplayName("a benefit with fewer than 3 words or a placeholder is flagged")
        void thin_benefit() {
            UserStory thin = story("analista", "exportar el backlog a CSV", "ahorrar tiempo", 3);
            thin.addAcceptanceCriterion(null, "a", "b", GOOD_THEN);
            UserStory placeholder = story("analista", "exportar el backlog a CSV", "n/a", 3);
            placeholder.addAcceptanceCriterion(null, "a", "b", GOOD_THEN);

            assertThat(finding(thin, QualityCharacteristic.COMPLETENESS).observation()).contains("beneficio");
            assertThat(finding(placeholder, QualityCharacteristic.COMPLETENESS).score()).isEqualTo(75);
        }

        @Test
        @DisplayName("missing parts add up and the suggestions say how to fix each")
        void adds_up() {
            UserStory story = story("TBD", "exportar", "n/a", 3);

            QualityFinding finding = finding(story, QualityCharacteristic.COMPLETENESS);

            assertThat(finding.score()).isZero();
            assertThat(finding.observation()).contains("Falta el rol").contains("acción").contains("beneficio")
                    .contains("criterios de aceptación");
            assertThat(finding.suggestion()).contains("Indica quién").contains("Describe qué").contains("Explica para qué")
                    .contains("Agrega");
        }
    }

    @Nested
    @DisplayName("language")
    class Languages {

        @Test
        @DisplayName("writes observations and suggestions in Spanish for a Spanish story")
        void spanish() {
            QualityFinding finding = finding(story("usuario", "ver pedidos rápido", "controlarlos mejor", 3),
                    QualityCharacteristic.AMBIGUITY);

            assertThat(finding.observation()).startsWith("Se detectaron términos ambiguos");
        }

        @Test
        @DisplayName("writes them in English when the story is clearly in English")
        void english() {
            UserStory story = story("compliance analyst", "to export the backlog quickly", "so that I can share it", 3);

            List<QualityFinding> findings = evaluator.evaluate(story);

            assertThat(findings.get(0).observation()).startsWith("Ambiguous terms were found").contains("\"fast\"");
            assertThat(findings.get(2).observation()).contains("no acceptance criteria");
            assertThat(findings.get(2).suggestion()).contains("Given/When/Then");
        }

        @Test
        @DisplayName("defaults to Spanish when the language is unclear")
        void defaults_to_spanish() {
            assertThat(QualityText.detectLanguage("CSV backlog")).isEqualTo(QualityText.Language.ES);
            assertThat(QualityText.detectLanguage("")).isEqualTo(QualityText.Language.ES);
        }
    }

    @Test
    @DisplayName("every finding the evaluator produces satisfies the domain invariants and the overall score is in range")
    void findings_are_valid() {
        List<UserStory> stories = List.of(
                goodStory(),
                story("usuario", "crear y editar pedidos rápido, etc.", "mejor", 21),
                story("TBD", "x", "n/a", null));

        for (UserStory story : stories) {
            List<QualityFinding> findings = evaluator.evaluate(story);
            assertThat(findings).hasSize(4).allSatisfy(f -> assertThat(f.score()).isBetween(0, 100));
            assertThat(StoryQualityAssessment.overallScore(findings)).isBetween(0, 100);
        }
    }
}
