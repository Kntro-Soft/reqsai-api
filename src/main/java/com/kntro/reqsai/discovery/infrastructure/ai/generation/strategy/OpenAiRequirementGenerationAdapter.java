package com.kntro.reqsai.discovery.infrastructure.ai.generation.strategy;

import tools.jackson.databind.ObjectMapper;
import com.kntro.reqsai.discovery.infrastructure.exception.DiscoveryInfrastructureExceptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;

/**
 * {@link AbstractLlmGenerationAdapter} backed by OpenAI GPT-4o-mini via Spring AI.
 * Active when {@code reqsai.ai.generation.provider=openai}.
 * Requires {@code OPENAI_API_KEY} to be set.
 */
@Slf4j
public class OpenAiRequirementGenerationAdapter extends AbstractLlmGenerationAdapter {

    private final ObjectProvider<OpenAiChatModel> chatModel;

    public OpenAiRequirementGenerationAdapter(ObjectProvider<OpenAiChatModel> chatModel, ObjectMapper objectMapper) {
        super(objectMapper);
        this.chatModel = chatModel;
    }

    @Override
    public boolean isAvailable() {
        return chatModel.getIfAvailable() != null;
    }

    @Override
    protected String modelName() {
        return "OpenAI";
    }

    @Override
    protected String callModel(String promptText) {
        OpenAiChatModel model = chatModel.getIfAvailable();
        if (model == null) {
            throw DiscoveryInfrastructureExceptions.generationUnavailable();
        }
        return callAndExtractText(model, promptText);
    }
}
