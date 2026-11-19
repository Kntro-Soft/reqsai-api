package com.kntro.reqsai.discovery.infrastructure.ai.improvement;

import com.kntro.reqsai.discovery.application.port.StoryDraft;
import com.kntro.reqsai.discovery.application.port.StoryImprovementRequest;
import com.kntro.reqsai.discovery.application.port.TokenUsageRecorderPort;
import com.kntro.reqsai.discovery.application.port.UnparseableGenerationException;
import com.kntro.reqsai.shared.application.ai.AiFeature;
import com.kntro.reqsai.shared.application.ai.AiUsageScope;
import com.kntro.reqsai.shared.application.ai.TokenUsage;
import com.kntro.reqsai.shared.domain.exception.InfrastructureException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Discovery: story improvement adapter")
class LlmStoryImprovementAdapterTest {

    private static final StoryImprovementRequest REQUEST = new StoryImprovementRequest(
            new StoryDraft("Exportar", "usuario", "exportar", "mejor", List.of()), List.of(), null, List.of());
    private static final String REPLY = "```json\n{\"proposal\": {\"title\": \"T\", \"role\": \"r\", "
            + "\"action\": \"a\", \"benefit\": \"b\"}}\n```";

    private static ObjectProvider<ChatModel> provider(ChatModel model) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        if (model != null) {
            factory.addBean("chatModel", model);
        }
        return factory.getBeanProvider(ChatModel.class);
    }

    private static ObjectProvider<org.springframework.ai.openai.OpenAiChatModel> noOpenAi() {
        return new StaticListableBeanFactory().getBeanProvider(org.springframework.ai.openai.OpenAiChatModel.class);
    }

    private static ChatModel replying(String text, List<Prompt> seen) {
        return prompt -> {
            seen.add(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage(text))),
                    ChatResponseMetadata.builder().id("call-1").model("test-model")
                            .usage(new DefaultUsage(120, 80, 200)).build());
        };
    }

    @Test
    @DisplayName("reads the proposal from the reply and meters the call as STORY_IMPROVEMENT of the surrounding scope")
    void proposes_and_meters() {
        List<Prompt> seen = new ArrayList<>();
        List<TokenUsage> metered = new ArrayList<>();
        TokenUsageRecorderPort recorder = new TokenUsageRecorderPort() {
            @Override
            public void record(long totalTokens) {
            }

            @Override
            public void record(TokenUsage usage) {
                metered.add(usage);
            }
        };
        var adapter = new LlmStoryImprovementAdapter("gemini", provider(replying(REPLY, seen)), noOpenAi(),
                new ObjectMapper(), recorder);
        UUID project = UUID.randomUUID();

        var result = AiUsageScope.call(AiFeature.STORY_IMPROVEMENT, project, () -> adapter.improve(REQUEST));

        assertThat(adapter.isAvailable()).isTrue();
        assertThat(result.proposal().title()).isEqualTo("T");
        assertThat(seen).hasSize(1);
        assertThat(seen.getFirst().getContents()).contains("Title: Exportar").contains("NEVER INVENT");
        assertThat(metered).hasSize(1);
        assertThat(metered.getFirst().feature()).isEqualTo(AiFeature.STORY_IMPROVEMENT);
        assertThat(metered.getFirst().inputTokens()).isEqualTo(120);
        assertThat(metered.getFirst().outputTokens()).isEqualTo(80);
    }

    @Test
    @DisplayName("without a chat model it is unavailable and a call fails with the generation-unavailable error")
    void unavailable_without_a_model() {
        var adapter = new LlmStoryImprovementAdapter("gemini", provider(null), noOpenAi(), new ObjectMapper(), t -> { });
        var openAi = new LlmStoryImprovementAdapter("openai", provider(replying(REPLY, new ArrayList<>())), noOpenAi(),
                new ObjectMapper(), t -> { });

        assertThat(adapter.isAvailable()).isFalse();
        assertThat(openAi.isAvailable()).isFalse();
        assertThatThrownBy(() -> adapter.improve(REQUEST)).isInstanceOf(InfrastructureException.class);
    }

    @Test
    @DisplayName("a reply that is not the JSON contract is an UnparseableGenerationException; an empty one too")
    void unparseable_reply() {
        var prose = new LlmStoryImprovementAdapter("gemini", provider(replying("Claro, aquí tienes.", new ArrayList<>())),
                noOpenAi(), new ObjectMapper(), t -> { });
        var empty = new LlmStoryImprovementAdapter("gemini", provider(replying(" ", new ArrayList<>())),
                noOpenAi(), new ObjectMapper(), t -> { });

        assertThatThrownBy(() -> prose.improve(REQUEST)).isInstanceOf(UnparseableGenerationException.class);
        assertThatThrownBy(() -> empty.improve(REQUEST)).isInstanceOf(UnparseableGenerationException.class);
    }
}
