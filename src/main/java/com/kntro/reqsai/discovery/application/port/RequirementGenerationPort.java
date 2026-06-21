package com.kntro.reqsai.discovery.application.port;

import org.jspecify.annotations.Nullable;

/**
 * Output port for AI-based user-story extraction from a session transcript.
 * Implementations may delegate to Gemini, GPT-4, or any other generative model.
 */
public interface RequirementGenerationPort {

    /** Returns {@code true} if the underlying AI model is configured and reachable. */
    boolean isAvailable();

    /**
     * Analyzes the given transcript and extracts a structured list of user stories.
     *
     * @param transcript full text of the session transcript (or a recent window for realtime)
     * @param language   BCP-47 language tag (e.g. {@code "es-PE"})
     * @return structured extraction result containing the generated stories
     */
    GenerationResult generate(String transcript, String language);

    /**
     * Analyzes the given transcript enriched with project context and extracts user stories.
     * When {@code context} is {@code null}, falls back to {@link #generate(String, String)}.
     *
     * @param transcript transcript text to analyze
     * @param language   BCP-47 language tag
     * @param context    optional project context to guide the model (tech stack, constraints, glossary)
     */
    default GenerationResult generate(String transcript, String language, @Nullable GenerationContext context) {
        return generate(transcript, language);
    }
}
