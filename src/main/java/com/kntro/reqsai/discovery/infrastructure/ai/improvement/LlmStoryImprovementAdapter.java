package com.kntro.reqsai.discovery.infrastructure.ai.improvement;

import com.kntro.reqsai.discovery.application.port.StoryImprovementPort;
import com.kntro.reqsai.discovery.application.port.StoryImprovementRequest;
import com.kntro.reqsai.discovery.application.port.StoryImprovementResult;
import com.kntro.reqsai.discovery.application.port.TokenUsageRecorderPort;
import com.kntro.reqsai.discovery.infrastructure.exception.DiscoveryInfrastructureExceptions;
import com.kntro.reqsai.shared.application.ai.AiFeature;
import com.kntro.reqsai.shared.application.ai.AiUsageScope;
import com.kntro.reqsai.shared.application.ai.TokenUsage;
import com.kntro.reqsai.shared.infrastructure.ai.usage.ChatUsage;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;

import java.util.Locale;

/**
 * {@link StoryImprovementPort} backed by the chat model the rest of the AI pipeline uses, selected like the
 * generation adapters by {@code reqsai.ai.generation.provider}: {@code openai} calls {@link OpenAiChatModel} in JSON
 * mode, any other value the generic {@link ChatModel} (Gemini in production, or a local model behind either). One
 * call per proposal; the reply must be the JSON contract of {@link StoryImprovementPrompt}, otherwise the call
 * fails with an {@code UnparseableGenerationException} and the caller may ask again. The tokens of each call are
 * handed to the {@link TokenUsageRecorderPort}, best effort, under the feature of the surrounding
 * {@link AiUsageScope} ({@link AiFeature#STORY_IMPROVEMENT} by default).
 */
@Slf4j
public class LlmStoryImprovementAdapter implements StoryImprovementPort {

    private final String provider;
    private final ObjectProvider<ChatModel> chatModel;
    private final ObjectProvider<OpenAiChatModel> openAiChatModel;
    private final ObjectMapper objectMapper;
    private final TokenUsageRecorderPort tokenUsageRecorder;

    public LlmStoryImprovementAdapter(String provider, ObjectProvider<ChatModel> chatModel,
                                      ObjectProvider<OpenAiChatModel> openAiChatModel, ObjectMapper objectMapper,
                                      TokenUsageRecorderPort tokenUsageRecorder) {
        this.provider = provider == null ? "" : provider.strip().toLowerCase(Locale.ROOT);
        this.chatModel = chatModel;
        this.openAiChatModel = openAiChatModel;
        this.objectMapper = objectMapper;
        this.tokenUsageRecorder = tokenUsageRecorder;
    }

    @Override
    public boolean isAvailable() {
        return usesOpenAi() ? openAiChatModel.getIfAvailable() != null : chatModel.getIfAvailable() != null;
    }

    @Override
    public StoryImprovementResult improve(StoryImprovementRequest request) {
        String prompt = StoryImprovementPrompt.build(request);
        if (log.isTraceEnabled()) {
            log.trace("=== STORY IMPROVEMENT PROMPT ===\n{}\n=== END PROMPT ===", prompt);
        }
        String reply = stripMarkdown(call(prompt));
        log.debug("Story improvement reply ({} chars)", reply.length());
        return StoryImprovementParser.parse(objectMapper, reply);
    }

    private boolean usesOpenAi() {
        return "openai".equals(provider);
    }

    private String call(String prompt) {
        ChatResponse response;
        if (usesOpenAi()) {
            OpenAiChatModel model = openAiChatModel.getIfAvailable();
            if (model == null) {
                throw DiscoveryInfrastructureExceptions.generationUnavailable();
            }
            response = model.call(new Prompt(prompt, OpenAiChatOptions.builder()
                    .responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build())
                    .build()));
        } else {
            ChatModel model = chatModel.getIfAvailable();
            if (model == null) {
                throw DiscoveryInfrastructureExceptions.generationUnavailable();
            }
            response = model.call(new Prompt(prompt));
        }
        recordTokenUsage(response);
        var result = response != null ? response.getResult() : null;
        String text = result != null ? result.getOutput().getText() : null;
        if (text == null || text.isBlank()) {
            throw DiscoveryInfrastructureExceptions.generationOutputUnparseable("Empty response from AI model", null);
        }
        return text;
    }

    /** Best-effort metering: a failure here must never fail the proposal. */
    private void recordTokenUsage(@Nullable ChatResponse response) {
        try {
            TokenUsage usage = ChatUsage.read(response, AiUsageScope.featureOr(AiFeature.STORY_IMPROVEMENT));
            if (usage != null) {
                tokenUsageRecorder.record(usage);
            }
        } catch (RuntimeException e) {
            log.debug("Token usage metering skipped for the story improvement: {}", e.getMessage());
        }
    }

    /** Strips optional Markdown code fences that some models wrap around JSON. */
    static String stripMarkdown(String text) {
        String s = text.strip();
        if (s.startsWith("```json")) s = s.substring(7);
        else if (s.startsWith("```")) s = s.substring(3);
        if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
        return s.strip();
    }
}
