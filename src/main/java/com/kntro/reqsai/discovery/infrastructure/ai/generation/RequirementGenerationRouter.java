package com.kntro.reqsai.discovery.infrastructure.ai.generation;

import com.kntro.reqsai.discovery.application.port.GenerationResult;
import com.kntro.reqsai.discovery.application.port.RequirementGenerationPort;
import com.kntro.reqsai.discovery.infrastructure.ai.generation.strategy.GeminiRequirementGenerationAdapter;
import com.kntro.reqsai.discovery.infrastructure.ai.generation.strategy.OpenAiRequirementGenerationAdapter;
import lombok.extern.slf4j.Slf4j;

/**
 * The single {@link RequirementGenerationPort} registered in the application context.
 * Selects the LLM provider at runtime based on {@code reqsai.ai.generation.provider}
 * (default: {@code gemini}).
 *
 * <ul>
 *   <li>{@code gemini}  — Gemini 2.0 Flash via Spring AI Google GenAI
 *   <li>{@code openai}  — GPT-4o-mini via Spring AI OpenAI
 * </ul>
 *
 * <p>Not a {@code @Component} — instantiated by {@code GenerationConfiguration} with
 * {@code @ConditionalOnMissingBean} so tests can replace it with a stub.
 */
@Slf4j
public class RequirementGenerationRouter implements RequirementGenerationPort {

    private final String provider;
    private final GeminiRequirementGenerationAdapter gemini;
    private final OpenAiRequirementGenerationAdapter openAi;

    public RequirementGenerationRouter(String provider, GeminiRequirementGenerationAdapter gemini, OpenAiRequirementGenerationAdapter openAi) {
        this.provider = provider;
        this.gemini = gemini;
        this.openAi = openAi;
    }

    @Override
    public boolean isAvailable() {
        return activeAdapter().isAvailable();
    }

    @Override
    public GenerationResult generate(String transcript, String language) {
        log.debug("Routing requirement generation to provider '{}'", provider);
        return activeAdapter().generate(transcript, language);
    }

    private RequirementGenerationPort activeAdapter() {
        return switch (provider) {
            case "openai" -> openAi;
            default       -> gemini;
        };
    }
}
