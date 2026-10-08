package com.kntro.reqsai.discovery.application.port;

import org.jspecify.annotations.Nullable;

import java.util.List;

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

    /**
     * Reads one message the analyst typed in the assistant chat: answers a question about the project
     * from {@code context} and {@code overview}, and/or restates the requirement it asks for so the
     * caller can turn it into suggestions with {@link #generate(String, String, GenerationContext)}.
     * The default treats the whole message as a requirement with no reply text, so a minimal
     * implementation (e.g. a test stub) still produces suggestions.
     *
     * @param message  what the analyst typed (untrusted)
     * @param history  earlier turns of the chat, oldest first (untrusted)
     * @param context  project profile, glossary, constraints and the related backlog stories
     * @param overview backlog counts, newest stories and sessions
     */
    default AssistantReply converse(String message, List<ChatTurn> history, GenerationContext context,
                                    BacklogOverview overview) {
        return new AssistantReply("", message, null);
    }
}
