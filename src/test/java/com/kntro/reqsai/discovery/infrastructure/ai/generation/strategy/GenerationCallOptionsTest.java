package com.kntro.reqsai.discovery.infrastructure.ai.generation.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Provider-specific call options of the generation adapters, without a real LLM: the OpenAI adapter must
 * request JSON mode on every call, while the Gemini adapter keeps sending a plain prompt.
 */
@DisplayName("Infra: generation call options per provider")
class GenerationCallOptionsTest {

    private static final String EMPTY_REPLY = "{\"stories\":[],\"questions\":[]}";
    private static final String TRANSCRIPT = "necesitamos exportar el reporte mensual a PDF";

    @Test
    @DisplayName("should request response_format json_object from OpenAI")
    void should_request_json_object_from_openai() {
        // Arrange
        OpenAiChatModel model = mock(OpenAiChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(reply(EMPTY_REPLY));
        var adapter = new OpenAiRequirementGenerationAdapter(provider(model), new ObjectMapper(), tokens -> { });

        // Act
        adapter.generate(TRANSCRIPT, "es");

        // Assert
        Prompt sent = sentPrompt(model);
        assertThat(sent.getContents()).contains(TRANSCRIPT);
        assertThat(sent.getOptions()).isInstanceOfSatisfying(OpenAiChatOptions.class, options ->
                assertThat(options.getResponseFormat().getType()).isEqualTo(ResponseFormat.Type.JSON_OBJECT));
    }

    @Test
    @DisplayName("should keep the configured model and temperature when JSON mode is merged over the defaults")
    void should_keep_configured_defaults() {
        // Arrange — what Spring AI's ChatModel.buildRequestPrompt does with the per-call options
        OpenAiChatOptions defaults = OpenAiChatOptions.builder().model("gpt-4o-mini").temperature(0.2).build();

        // Act
        OpenAiChatOptions merged = defaults.mutate()
                .combineWith(OpenAiRequirementGenerationAdapter.jsonObjectOutput().mutate())
                .build();

        // Assert
        assertThat(merged.getModel()).isEqualTo("gpt-4o-mini");
        assertThat(merged.getTemperature()).isEqualTo(0.2);
        assertThat(merged.getResponseFormat().getType()).isEqualTo(ResponseFormat.Type.JSON_OBJECT);
    }

    @Test
    @DisplayName("should send Gemini a plain prompt, with no OpenAI options")
    void should_leave_gemini_prompt_unchanged() {
        // Arrange
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(reply(EMPTY_REPLY));
        var adapter = new GeminiRequirementGenerationAdapter(provider(model), new ObjectMapper(), tokens -> { });

        // Act
        var result = adapter.generate(TRANSCRIPT, "es");

        // Assert
        Prompt sent = sentPrompt(model);
        assertThat(sent.getContents()).contains(TRANSCRIPT);
        assertThat(sent.getOptions() instanceof OpenAiChatOptions).isFalse();
        assertThat(result.stories()).isEmpty();
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

    private static Prompt sentPrompt(ChatModel model) {
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(captor.capture());
        return captor.getValue();
    }
}
