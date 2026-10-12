package com.kntro.reqsai.discovery.infrastructure.ai.verification;

import com.kntro.reqsai.discovery.application.port.CodeVerificationPort;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/** Wires the model-backed code verification, unless a test already provided a {@link CodeVerificationPort}. */
@Configuration
class VerificationConfiguration {

    @Bean
    @ConditionalOnMissingBean(CodeVerificationPort.class)
    CodeVerificationPort codeVerification(ObjectProvider<ChatModel> chatModel,
                                          ObjectProvider<OpenAiChatModel> openAiChatModel,
                                          ObjectMapper objectMapper,
                                          @Value("${reqsai.ai.generation.provider:gemini}") String provider) {
        return new LlmCodeVerificationAdapter(provider, chatModel, openAiChatModel, objectMapper);
    }
}
