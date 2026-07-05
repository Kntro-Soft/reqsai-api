package com.kntro.reqsai.discovery.infrastructure.ai.generation.strategy;

import com.kntro.reqsai.discovery.application.port.GenerationContext;
import com.kntro.reqsai.discovery.application.port.GenerationResult;
import com.kntro.reqsai.discovery.domain.model.SuggestionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenario tests over prompt-assembly and response-parsing of {@link AbstractLlmGenerationAdapter}
 * WITHOUT calling a real LLM. A {@link StubAdapter} captures the prompt text sent to the model and
 * returns a canned JSON response, so we can assert both that the assembled prompt grounds the model
 * in the backlog / already-suggested list and that the parser maps each response shape correctly.
 *
 * <p>Imagines a requirements meeting and the range of things people say:
 * revisit-and-extend, same-capability facet, paraphrase, garbled STT, genuinely new, and a question.
 * The dedup and classification of these parsed results is covered by
 * {@code SuggestionCreationServiceTest}; here we focus on prompt+parse.
 */
@DisplayName("Infra: LLM generation scenarios (prompt + parse, no real LLM)")
class GenerationScenarioTest {

    /** Test double: records the prompt, replays a scripted JSON response. */
    private static final class StubAdapter extends AbstractLlmGenerationAdapter {
        private final String cannedResponse;
        String capturedPrompt;

        StubAdapter(String cannedResponse) {
            super(new ObjectMapper());
            this.cannedResponse = cannedResponse;
        }

        @Override
        protected String callModel(String promptText) {
            this.capturedPrompt = promptText;
            return cannedResponse;
        }

        @Override
        protected String modelName() {
            return "Stub";
        }

        @Override
        public boolean isAvailable() {
            return true;
        }
    }

    private static final UUID PENDING_SUGGESTION_ID = UUID.randomUUID();

    private static GenerationContext contextWithLoginStory(UUID loginId) {
        return new GenerationContext(
                "PayApp", "Plataforma de pagos",
                List.of("Java"), List.of("Spring"), List.of("PostgreSQL"),
                "Hexagonal", "Fintech",
                List.of("PCI-DSS"),
                List.of(new GenerationContext.GlossaryEntry("2FA", "Segundo factor de autenticación")),
                List.of(new GenerationContext.StorySummary(
                        loginId, "Iniciar sesión", "usuario", "iniciar sesión con email y contraseña",
                        "acceder al sistema")),
                List.of(new GenerationContext.PendingSuggestion(PENDING_SUGGESTION_ID, "Recuperar contraseña")));
    }

    @Nested
    @DisplayName("Prompt assembly")
    class PromptAssembly {

        @Test
        @DisplayName("should render the backlog story ids, already-suggested list and the new rules")
        void should_render_grounding_and_rules() {
            UUID loginId = UUID.randomUUID();
            StubAdapter adapter = new StubAdapter("{\"stories\":[],\"questions\":[]}");

            adapter.generate("volviendo al inicio de sesión, además quiero 2FA", "es-PE",
                    contextWithLoginStory(loginId));

            String prompt = adapter.capturedPrompt;
            // Backlog grounding: the login story with its id must be visible so the model can target it.
            assertThat(prompt).contains(loginId.toString()).contains("Iniciar sesión");
            // Already-suggested guard renders (with the pending suggestion's id so the model can target it).
            assertThat(prompt).contains("ALREADY SUGGESTED THIS SESSION")
                    .contains("Recuperar contraseña")
                    .contains(PENDING_SUGGESTION_ID.toString());
            // The strengthened rules are present.
            assertThat(prompt).contains("UPDATE_STORY");
            assertThat(prompt).contains("volviendo a");          // revisit cue few-shot
            assertThat(prompt).contains("QUALITY BAR");           // garbled → nothing
            assertThat(prompt).contains("LANGUAGE CONSISTENCY");  // off-language fragment → omit
            assertThat(prompt).contains("GRANULARITY");           // facets → EDGE_CASE/UPDATE
            assertThat(prompt).contains("mantener la sesión activa"); // granularity example
            assertThat(prompt).contains("Given / When / Then");   // criteria instruction
        }
    }

    @Nested
    @DisplayName("Response parsing")
    class ResponseParsing {

        @Test
        @DisplayName("revisit-and-extend → UPDATE_STORY carrying the targeted story id")
        void revisit_extends_existing_story() {
            UUID loginId = UUID.randomUUID();
            String json = """
                {"stories":[{"type":"UPDATE_STORY","targetStoryId":"%s",
                  "title":"Iniciar sesión con 2FA","role":"usuario",
                  "action":"iniciar sesión con un segundo factor","benefit":"mayor seguridad",
                  "priority":"HIGH","storyPoints":3,"acceptanceCriteria":[]}],"questions":[]}
                """.formatted(loginId);
            StubAdapter adapter = new StubAdapter(json);

            GenerationResult result = adapter.generate("además quiero 2FA", "es-PE",
                    contextWithLoginStory(loginId));

            assertThat(result.stories()).hasSize(1);
            assertThat(result.stories().getFirst().type()).isEqualTo(SuggestionType.UPDATE_STORY);
            assertThat(result.stories().getFirst().targetStoryId()).isEqualTo(loginId);
        }

        @Test
        @DisplayName("criteria-level facet → EDGE_CASE with a relatedTopic, not a NEW_STORY")
        void facet_becomes_edge_case() {
            UUID loginId = UUID.randomUUID();
            String json = """
                {"stories":[{"type":"EDGE_CASE","targetStoryId":"%s",
                  "title":"Mantener la sesión activa","role":"usuario",
                  "action":"seguir autenticado","benefit":"no reingresar credenciales",
                  "priority":"MEDIUM","storyPoints":2,"relatedTopic":"inicio de sesión",
                  "acceptanceCriteria":[]}],"questions":[]}
                """.formatted(loginId);
            StubAdapter adapter = new StubAdapter(json);

            GenerationResult result = adapter.generate("y que mantenga la sesión activa", "es-PE",
                    contextWithLoginStory(loginId));

            assertThat(result.stories().getFirst().type()).isEqualTo(SuggestionType.EDGE_CASE);
            assertThat(result.stories().getFirst().relatedTopic()).isEqualTo("inicio de sesión");
            assertThat(result.stories().getFirst().targetStoryId()).isEqualTo(loginId);
        }

        @Test
        @DisplayName("genuinely new capability → NEW_STORY with 2-4 parsed Given/When/Then criteria")
        void genuinely_new_story_with_criteria() {
            String json = """
                {"stories":[{"type":"NEW_STORY","targetStoryId":null,
                  "title":"Exportar reportes a PDF","role":"analista",
                  "action":"exportar reportes en PDF","benefit":"compartirlos con clientes",
                  "priority":"HIGH","storyPoints":5,
                  "acceptanceCriteria":[
                    {"scenario":"Reporte listo","given":"un reporte generado","when":"elige exportar a PDF","then":"descarga un PDF"},
                    {"scenario":null,"given":"sin datos","when":"elige exportar","then":"ve un aviso"}
                  ]}],"questions":[]}
                """;
            StubAdapter adapter = new StubAdapter(json);

            GenerationResult result = adapter.generate("necesito exportar reportes a PDF", "es-PE", null);

            GenerationResult.GeneratedStory story = result.stories().getFirst();
            assertThat(story.type()).isEqualTo(SuggestionType.NEW_STORY);
            assertThat(story.targetStoryId()).isNull();
            assertThat(story.acceptanceCriteria()).hasSize(2);
            assertThat(story.acceptanceCriteria().getFirst().given()).isEqualTo("un reporte generado");
            assertThat(story.acceptanceCriteria().getLast().scenario()).isNull();
        }

        @Test
        @DisplayName("garbled transcript → the model emits nothing, parser yields no stories/questions")
        void garbled_yields_nothing() {
            StubAdapter adapter = new StubAdapter("{\"stories\":[],\"questions\":[]}");

            GenerationResult result = adapter.generate(
                    "Como User, quiero el inicio de decisión, para debe ser seguro", "es-PE", null);

            assertThat(result.stories()).isEmpty();
            assertThat(result.questions()).isEmpty();
        }

        @Test
        @DisplayName("ambiguous ask → CLARIFYING_QUESTION in the questions array, no story")
        void ambiguous_becomes_question() {
            String json = """
                {"stories":[],"questions":[{"question":"¿Qué roles de usuario deben existir?"}]}
                """;
            StubAdapter adapter = new StubAdapter(json);

            GenerationResult result = adapter.generate("los usuarios tendrán permisos", "es-PE", null);

            assertThat(result.stories()).isEmpty();
            assertThat(result.questions()).hasSize(1);
            assertThat(result.questions().getFirst().question()).contains("roles");
        }

        @Test
        @DisplayName("markdown-fenced JSON is tolerated by the parser")
        void tolerates_markdown_fences() {
            String json = "```json\n{\"stories\":[],\"questions\":[]}\n```";
            StubAdapter adapter = new StubAdapter(json);

            GenerationResult result = adapter.generate("texto", "es-PE", null);

            assertThat(result.stories()).isEmpty();
        }
    }
}
