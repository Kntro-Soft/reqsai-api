package com.kntro.reqsai.discovery.infrastructure.ai.improvement;

import com.kntro.reqsai.discovery.application.port.StoryImprovementPort;
import com.kntro.reqsai.discovery.application.port.TokenUsageRecorderPort;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/** Wires the model-backed story improvement, unless a test already provided a {@link StoryImprovementPort}. */
@Configuration
class StoryImprovementConfiguration {

    @Bean
    @ConditionalOnMissingBean(StoryImprovementPort.class)
    StoryImprovementPort storyImprovement(ObjectProvider<ChatModel> chatModel,
                                          ObjectProvider<OpenAiChatModel> openAiChatModel,
                                          ObjectMapper objectMapper,
                                          TokenUsageRecorderPort tokenUsageRecorder,
                                          @Value("${reqsai.ai.generation.provider:gemini}") String provider) {
        return new LlmStoryImprovementAdapter(provider, chatModel, openAiChatModel, objectMapper, tokenUsageRecorder);
    }
}
