package com.kntro.reqsai.discovery.application.port;

/**
 * Output port for AI-based user-story extraction from a session transcript.
 * Implementations may delegate to Gemini, GPT-4, or any other generative model.
 */
public interface RequirementGenerationPort {

    /** Returns {@code true} if the underlying AI model is configured and reachable. */
    boolean isAvailable();

    /**
     * Analyzes the given transcript text and extracts a structured list of user stories
     * with acceptance criteria.
     *
     * @param transcript full text of the session transcript
     * @param language   BCP-47 language tag (e.g. {@code "es-PE"}) to guide language-aware extraction
     * @return structured extraction result containing the generated stories
     */
    GenerationResult generate(String transcript, String language);
}
