package com.kntro.reqsai.workspace.infrastructure.ai;

import com.kntro.reqsai.billing.api.BillingModuleApi;
import com.kntro.reqsai.workspace.application.port.DocumentClassificationPort;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Registers the {@link LlmDocumentClassificationAdapter} as the single {@link DocumentClassificationPort}
 * bean — unless a test already provided one ({@code @ConditionalOnMissingBean}). It follows the same
 * provider switch as requirement generation ({@code reqsai.ai.generation.provider}).
 */
@Configuration
class DocumentClassificationConfiguration {

    @Bean
    @ConditionalOnMissingBean(DocumentClassificationPort.class)
    DocumentClassificationPort documentClassificationAdapter(
            ObjectProvider<ChatModel> chatModel,
            ObjectProvider<OpenAiChatModel> openAiChatModel,
            ObjectMapper objectMapper,
            BillingModuleApi billing,
            @Value("${reqsai.ai.generation.provider:gemini}") String provider) {
        return new LlmDocumentClassificationAdapter(provider, chatModel, openAiChatModel, objectMapper, billing);
    }
}
