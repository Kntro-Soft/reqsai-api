package com.kntro.reqsai.discovery.infrastructure.ai.generation.strategy;

import tools.jackson.databind.ObjectMapper;
import com.kntro.reqsai.discovery.application.port.GenerationResult;
import com.kntro.reqsai.discovery.infrastructure.exception.DiscoveryInfrastructureExceptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;

/**
 * {@link AbstractLlmGenerationAdapter} backed by the Gemini generative model via Spring AI's
 * generic {@link ChatModel}. Active when {@code reqsai.ai.generation.provider=gemini} (default).
 */
@Slf4j
public class GeminiRequirementGenerationAdapter extends AbstractLlmGenerationAdapter {

    private final ObjectProvider<ChatModel> chatModel;

    public GeminiRequirementGenerationAdapter(ObjectProvider<ChatModel> chatModel, ObjectMapper objectMapper) {
        super(objectMapper);
        this.chatModel = chatModel;
    }

    @Override
    public boolean isAvailable() {
        return chatModel.getIfAvailable() != null;
    }

    @Override
    public GenerationResult generate(String transcript, String language) {
        ChatModel model = chatModel.getIfAvailable();
        if (model == null) {
            throw DiscoveryInfrastructureExceptions.generationUnavailable();
        }
        log.debug("Sending extraction prompt to Gemini ({} chars)", transcript.length());
        String rawResponse = callAndExtractText(model, EXTRACTION_PROMPT.formatted(transcript));
        String json = stripMarkdown(rawResponse);
        log.debug("Gemini response ({} chars)", json.length());
        return parseJsonResponse(json, "Gemini");
    }
}
