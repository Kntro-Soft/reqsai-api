package com.kntro.reqsai.discovery.application.port;

/**
 * Output port for the AI that proposes an improved version of a story (US73). It only proposes: nothing it
 * returns is stored, and applying a proposal is a separate, explicit step that goes through the domain.
 * <p>
 * Like the other AI ports, an implementation follows {@code reqsai.ai.generation.provider}, so the provider switch
 * (Gemini, OpenAI, a local model behind either) applies to it too.
 */
public interface StoryImprovementPort {

    /** Returns {@code true} if the underlying AI model is configured and reachable. */
    boolean isAvailable();

    /**
     * Proposes an improved version of the story. The text it returns must never state a number, limit, name or
     * rule that the request does not state: what is missing is a {@code [[...]]} token declared in the result.
     *
     * @throws UnparseableGenerationException when the model replied with something that is not the contract
     */
    StoryImprovementResult improve(StoryImprovementRequest request);
}
