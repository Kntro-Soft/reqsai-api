package com.kntro.reqsai.shared.infrastructure.ai.usage;

import com.kntro.reqsai.shared.application.ai.AiFeature;
import com.kntro.reqsai.shared.application.ai.AiUsageReporter;
import com.kntro.reqsai.shared.application.ai.TokenUsage;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.google.genai.metadata.GoogleGenAiUsage;

/**
 * Turns a Spring AI chat response into a {@link TokenUsage} and reports it. Shared by every adapter that
 * calls a chat model, so they all read input, output, cached input, model and call id the same way.
 */
public final class ChatUsage {

    private static final Logger log = LoggerFactory.getLogger(ChatUsage.class);

    private ChatUsage() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Reads the usage of {@code response} and reports it under {@code defaultFeature} (the surrounding
     * {@code AiUsageScope}, when there is one, wins). Never throws.
     */
    public static void report(AiUsageReporter reporter, @Nullable ChatResponse response, AiFeature defaultFeature) {
        try {
            TokenUsage usage = read(response, defaultFeature);
            if (usage != null) {
                reporter.report(usage);
            }
        } catch (RuntimeException e) {
            log.debug("Token usage skipped: {}", e.getMessage());
        }
    }

    /** The usage a response carries, or {@code null} when the provider reported none. */
    public static @Nullable TokenUsage read(@Nullable ChatResponse response, AiFeature feature) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null) {
            return null;
        }
        long input = value(usage.getPromptTokens());
        long output = value(usage.getCompletionTokens());
        long total = value(usage.getTotalTokens());
        // Reasoning ("thinking") tokens are billed as output but are not in the completion count of some
        // providers: whatever the total holds beyond input and output is output.
        output = Math.max(output, total - input);
        if (input + output <= 0) {
            return null;
        }
        String id = response.getMetadata().getId();
        String model = response.getMetadata().getModel();
        return TokenUsage.of(feature, null, model == null || model.isBlank() ? null : model, input, output,
                cachedInput(usage), id == null || id.isBlank() ? null : id);
    }

    private static long cachedInput(Usage usage) {
        if (usage instanceof GoogleGenAiUsage gemini && gemini.getCachedContentTokenCount() != null) {
            return gemini.getCachedContentTokenCount();
        }
        Long cached = usage.getCacheReadInputTokens();
        return cached == null ? 0 : cached;
    }

    private static long value(@Nullable Number number) {
        return number == null ? 0 : number.longValue();
    }
}
