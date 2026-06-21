package com.kntro.reqsai.discovery.infrastructure.ai.generation.config;

import tools.jackson.databind.ObjectMapper;
import com.kntro.reqsai.discovery.application.port.RequirementGenerationPort;
import com.kntro.reqsai.discovery.infrastructure.ai.generation.RequirementGenerationRouter;
import com.kntro.reqsai.discovery.infrastructure.ai.generation.strategy.GeminiRequirementGenerationAdapter;
import com.kntro.reqsai.discovery.infrastructure.ai.generation.strategy.OpenAiRequirementGenerationAdapter;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the requirement generation infrastructure: instantiates each LLM adapter and registers a
 * {@link RequirementGenerationRouter} as the single {@link RequirementGenerationPort} bean —
 * unless a test already provided one ({@code @ConditionalOnMissingBean}).
 */
@Configuration
class GenerationConfiguration {

    @Bean
    @ConditionalOnMissingBean(RequirementGenerationPort.class)
    RequirementGenerationPort requirementGenerationRouter(
            ObjectProvider<ChatModel> chatModel,
            ObjectProvider<OpenAiChatModel> openAiChatModel,
            ObjectMapper objectMapper,
            @Value("${reqsai.ai.generation.provider:gemini}") String provider) {
        return new RequirementGenerationRouter(
                provider,
                new GeminiRequirementGenerationAdapter(chatModel, objectMapper),
                new OpenAiRequirementGenerationAdapter(openAiChatModel, objectMapper)
        );
    }
}
