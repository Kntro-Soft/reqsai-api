package com.kntro.reqsai.discovery.infrastructure.ai.generation.strategy;

import tools.jackson.databind.ObjectMapper;
import com.kntro.reqsai.discovery.application.port.TokenUsageRecorderPort;
import com.kntro.reqsai.discovery.infrastructure.exception.DiscoveryInfrastructureExceptions;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;

/**
 * {@link AbstractLlmGenerationAdapter} backed by the Gemini generative model via Spring AI's
 * generic {@link ChatModel}. Active when {@code reqsai.ai.generation.provider=gemini} (default).
 */
@Slf4j
public class GeminiRequirementGenerationAdapter extends AbstractLlmGenerationAdapter {

    private final ObjectProvider<ChatModel> chatModel;

    public GeminiRequirementGenerationAdapter(ObjectProvider<ChatModel> chatModel, ObjectMapper objectMapper,
                                              TokenUsageRecorderPort tokenUsageRecorder) {
        this(chatModel, objectMapper, tokenUsageRecorder, null);
    }

    public GeminiRequirementGenerationAdapter(ObjectProvider<ChatModel> chatModel, ObjectMapper objectMapper,
                                              TokenUsageRecorderPort tokenUsageRecorder,
                                              @Nullable String liveModel) {
        super(objectMapper, tokenUsageRecorder, liveModel);
        this.chatModel = chatModel;
    }

    @Override
    public boolean isAvailable() {
        return chatModel.getIfAvailable() != null;
    }

    @Override
    protected String modelName() {
        return "Gemini";
    }

    @Override
    protected String callModel(String promptText) {
        ChatModel model = chatModel.getIfAvailable();
        if (model == null) {
            throw DiscoveryInfrastructureExceptions.generationUnavailable();
        }
        return callAndExtractText(model, promptText);
    }

    @Override
    protected String callModel(String promptText, String model) {
        ChatModel chat = chatModel.getIfAvailable();
        if (chat == null) {
            throw DiscoveryInfrastructureExceptions.generationUnavailable();
        }
        return callAndExtractText(chat, new Prompt(promptText, GoogleGenAiChatOptions.builder().model(model).build()));
    }
}
