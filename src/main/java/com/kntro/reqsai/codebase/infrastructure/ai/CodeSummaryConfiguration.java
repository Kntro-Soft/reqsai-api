package com.kntro.reqsai.codebase.infrastructure.ai;

import com.kntro.reqsai.billing.api.BillingModuleApi;
import com.kntro.reqsai.codebase.application.port.CodeSummaryPort;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Registers {@link LlmCodeSummaryAdapter} as the {@link CodeSummaryPort} unless a test provides one. It follows
 * the provider switch of requirement generation ({@code reqsai.ai.generation.provider}).
 */
@Configuration
class CodeSummaryConfiguration {

    @Bean
    @ConditionalOnMissingBean(CodeSummaryPort.class)
    CodeSummaryPort codeSummaryAdapter(ObjectProvider<ChatModel> chatModel,
                                       ObjectProvider<OpenAiChatModel> openAiChatModel,
                                       ObjectMapper objectMapper, BillingModuleApi billing,
                                       @Value("${reqsai.ai.generation.provider:gemini}") String provider) {
        return new LlmCodeSummaryAdapter(provider, chatModel, openAiChatModel, objectMapper, billing);
    }
}
