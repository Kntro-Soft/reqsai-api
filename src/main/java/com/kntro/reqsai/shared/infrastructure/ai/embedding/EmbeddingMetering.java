package com.kntro.reqsai.shared.infrastructure.ai.embedding;

import com.kntro.reqsai.shared.application.ai.AiFeature;
import com.kntro.reqsai.shared.application.ai.AiUsageReporter;
import com.kntro.reqsai.shared.application.ai.TokenUsage;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;

/**
 * Reports the tokens an embedding call used. The count comes from the provider's usage metadata when it
 * returns one (OpenAI does); otherwise it is estimated as one token per {@value #CHARS_PER_TOKEN}
 * characters, the usual average for Spanish and English text. Never throws.
 */
public final class EmbeddingMetering {

    /** Characters per token used when the provider reports no usage. */
    public static final int CHARS_PER_TOKEN = 4;

    private EmbeddingMetering() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** Embeds one text through Spring AI and reports the provider-counted (or estimated) tokens. */
    public static float[] embed(org.springframework.ai.embedding.EmbeddingModel model, String text,
                                AiUsageReporter reporter) {
        EmbeddingResponse response = model.embedForResponse(List.of(text));
        float[] vector = response.getResults().getFirst().getOutput();
        Integer counted = null;
        String modelName = null;
        if (response.getMetadata() != null) {
            modelName = response.getMetadata().getModel();
            if (response.getMetadata().getUsage() != null) {
                counted = response.getMetadata().getUsage().getPromptTokens();
            }
        }
        report(reporter, modelName, counted == null ? 0 : counted, text);
        return vector;
    }

    /** Reports {@code counted} tokens, or the estimate for {@code text} when the provider counted none. */
    public static void report(AiUsageReporter reporter, @Nullable String model, long counted, String text) {
        try {
            long tokens = counted > 0 ? counted : estimate(text);
            if (tokens > 0) {
                reporter.report(TokenUsage.of(AiFeature.EMBEDDINGS, null,
                        model == null || model.isBlank() ? null : model, tokens, 0, 0, null));
            }
        } catch (RuntimeException e) {
            // metering never breaks an embedding
        }
    }

    /** Estimated tokens of {@code text}: one per {@value #CHARS_PER_TOKEN} characters, rounded up. */
    public static long estimate(String text) {
        return text == null || text.isEmpty() ? 0 : (text.length() + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN;
    }
}
