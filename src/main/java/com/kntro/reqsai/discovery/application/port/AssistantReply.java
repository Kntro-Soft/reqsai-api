package com.kntro.reqsai.discovery.application.port;

import org.jspecify.annotations.Nullable;

/**
 * The assistant's reading of one analyst chat message (see {@link RequirementGenerationPort#converse}).
 *
 * @param reply       the text shown to the analyst: an answer, or a short note on what was understood
 * @param requirement the requirement the message asks for, restated so it reads on its own, or
 *                    {@code null} when the message only asks a question or is small talk
 * @param language    ISO-639-1 language of the analyst's message (e.g. {@code "es"}), when detected
 * @param searchCode  whether the message asks about what the existing code does and the module index did not
 *                    answer it clearly, so the caller should search the pure source code
 */
public record AssistantReply(String reply, @Nullable String requirement, @Nullable String language,
                             boolean searchCode) {

    public AssistantReply(String reply, @Nullable String requirement, @Nullable String language) {
        this(reply, requirement, language, false);
    }

    public boolean hasRequirement() {
        return requirement != null && !requirement.isBlank();
    }
}
