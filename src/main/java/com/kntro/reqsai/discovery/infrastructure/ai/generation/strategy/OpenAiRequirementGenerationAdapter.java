package com.kntro.reqsai.discovery.infrastructure.ai.generation.strategy;

import tools.jackson.databind.ObjectMapper;
import com.kntro.reqsai.discovery.application.port.TokenUsageRecorderPort;
import com.kntro.reqsai.discovery.infrastructure.exception.DiscoveryInfrastructureExceptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;

/**
 * {@link AbstractLlmGenerationAdapter} backed by OpenAI GPT-4o-mini via Spring AI.
 * Active when {@code reqsai.ai.generation.provider=openai}.
 * Requires {@code OPENAI_API_KEY} to be set.
 *
 * <p>Every call requests OpenAI's JSON mode ({@code response_format: json_object}), so the reply is
 * always one syntactically valid JSON object. Without it the model once returned malformed JSON
 * ({@code Unexpected close marker ']'}) that only the retry recovered. JSON mode, not {@code json_schema}:
 * Spring AI sends a schema with {@code strict: true}, which would require every field of the response
 * records to be listed as required, with the optional ones typed as nullable. That would change the
 * reply contract the prompts describe. The prompts already ask for "JSON", which JSON mode requires.
 * Model, temperature and the other configured options are kept: Spring AI merges these per-call
 * options over the defaults.
 */
@Slf4j
public class OpenAiRequirementGenerationAdapter extends AbstractLlmGenerationAdapter {

    private final ObjectProvider<OpenAiChatModel> chatModel;

    public OpenAiRequirementGenerationAdapter(ObjectProvider<OpenAiChatModel> chatModel, ObjectMapper objectMapper,
                                              TokenUsageRecorderPort tokenUsageRecorder) {
        super(objectMapper, tokenUsageRecorder);
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
        return callAndExtractText(model, new Prompt(promptText, jsonObjectOutput()));
    }

    static OpenAiChatOptions jsonObjectOutput() {
        return OpenAiChatOptions.builder()
                .responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build())
                .build();
    }
}
