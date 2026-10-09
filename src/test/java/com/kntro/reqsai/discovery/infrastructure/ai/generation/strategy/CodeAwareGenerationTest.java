package com.kntro.reqsai.discovery.infrastructure.ai.generation.strategy;

import com.kntro.reqsai.discovery.application.port.GenerationContext;
import com.kntro.reqsai.discovery.application.port.GenerationResult;
import com.kntro.reqsai.discovery.domain.model.CodeFinding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The code-aware copilot in the generation adapter, without a real LLM: the client's code reaches the prompt
 * as the EXISTING SYSTEM section, and the model's code finding, cited modules and evidence come back resolved.
 */
@DisplayName("Infra: code-aware generation")
class CodeAwareGenerationTest {

    private static final String TRANSCRIPT = "[Ana (Cliente)]: quiero que el comensal pueda cancelar hasta 24 horas antes";

    private static GenerationContext context() {
        GenerationContext base = new GenerationContext("Restaurante", null, List.of("TypeScript"), List.of("Express"),
                List.of("PostgreSQL"), null, "Gastronomía", List.of(), List.of(), List.of(), List.of());
        return base.withCode(new GenerationContext.CodeContext("Reservas en línea para La Tradición.", List.of(
                new GenerationContext.CodeModuleEntry("C1", "acme/reservas", "src/reservations", "Reservas",
                        "Crea y cancela reservas de mesas.", List.of("Cancelar una reserva"),
                        List.of("La cancelación solo se permite hasta 2 horas antes"),
                        "https://github.com/acme/reservas/tree/abc/src/reservations"))));
    }

    @Test
    @DisplayName("renders the client's code as the EXISTING SYSTEM section and the code rules in the prompt")
    void prompt() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(reply("{\"stories\":[],\"questions\":[]}"));
        var adapter = new GeminiRequirementGenerationAdapter(provider(model), new ObjectMapper(), tokens -> { });

        adapter.generate(TRANSCRIPT, "es-PE", context());

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(captor.capture());
        String prompt = captor.getValue().getContents();
        assertThat(prompt)
                .contains("EXISTING SYSTEM — the client's CURRENT code")
                .contains("Overview: Reservas en línea para La Tradición.")
                .contains("C1 | Reservas (acme/reservas: src/reservations) | Crea y cancela reservas de mesas.")
                .contains("Rules: La cancelación solo se permite hasta 2 horas antes")
                .contains("CODE AWARENESS")
                .contains("\"codeFinding\"")
                .contains("EVIDENCE");
    }

    @Test
    @DisplayName("resolves the cited module, keeps the conflict note and cleans the evidence quote")
    void parsesInsight() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(reply("""
                {"stories":[{"type":"NEW_STORY","title":"Cancelar reserva con 24 horas","role":"comensal",
                  "action":"cancelar mi reserva hasta 24 horas antes","benefit":"no perder mi dinero",
                  "priority":"HIGH","storyPoints":3,"acceptanceCriteria":[],
                  "codeFinding":"conflicts_with_code",
                  "codeNote":"El código permite cancelar hasta 2 h antes; el cliente pide 24 h",
                  "codeRefs":["C1","C9"],
                  "evidence":"[Ana (Cliente)]: \\"quiero que el comensal pueda cancelar hasta 24 horas antes\\""}],
                 "questions":[{"question":"¿Se cobra penalidad?","evidence":"cancelar hasta 24 horas antes"}]}
                """));
        var adapter = new GeminiRequirementGenerationAdapter(provider(model), new ObjectMapper(), tokens -> { });

        GenerationResult result = adapter.generate(TRANSCRIPT, "es-PE", context());

        var insight = result.stories().getFirst().insight();
        assertThat(insight).isNotNull();
        assertThat(insight.codeFinding()).isEqualTo(CodeFinding.CONFLICTS_WITH_CODE);
        assertThat(insight.codeNote()).contains("2 h", "24 h");
        assertThat(insight.codeReferences()).singleElement().satisfies(ref -> {
            assertThat(ref.repository()).isEqualTo("acme/reservas");
            assertThat(ref.path()).isEqualTo("src/reservations");
            assertThat(ref.name()).isEqualTo("Reservas");
            assertThat(ref.url()).endsWith("/src/reservations");
        });
        assertThat(insight.evidenceQuote()).isEqualTo("quiero que el comensal pueda cancelar hasta 24 horas antes");
        assertThat(result.questions().getFirst().evidenceQuote()).isEqualTo("cancelar hasta 24 horas antes");
    }

    @Test
    @DisplayName("ignores a code finding when the prompt had no connected code")
    void ignoresFindingWithoutCode() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(reply("""
                {"stories":[{"type":"NEW_STORY","title":"Cancelar reserva","role":"comensal","action":"cancelar",
                  "benefit":"flexibilidad","priority":"HIGH","acceptanceCriteria":[],
                  "codeFinding":"ALREADY_EXISTS","codeRefs":["C1"],"evidence":"cancelar mi reserva"}],"questions":[]}
                """));
        var adapter = new GeminiRequirementGenerationAdapter(provider(model), new ObjectMapper(), tokens -> { });
        GenerationContext noCode = context().withCode(null);

        var insight = adapter.generate(TRANSCRIPT, "es-PE", noCode).stories().getFirst().insight();

        assertThat(insight).isNotNull();
        assertThat(insight.codeFinding()).isNull();
        assertThat(insight.codeReferences()).isEmpty();
        assertThat(insight.evidenceQuote()).isEqualTo("cancelar mi reserva");
    }

    private static ChatResponse reply(String json) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T model) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(model);
        return provider;
    }
}
