package com.kntro.reqsai.discovery.application.port;

import org.jspecify.annotations.Nullable;

/**
 * The assistant's reading of one analyst chat message (see {@link RequirementGenerationPort#converse}).
 *
 * @param reply       the text shown to the analyst: an answer, or a short note on what was understood
 * @param requirement the requirement the message asks for, restated so it reads on its own, or
 *                    {@code null} when the message only asks a question or is small talk
 * @param language    ISO-639-1 language of the analyst's message (e.g. {@code "es"}), when detected
 */
public record AssistantReply(String reply, @Nullable String requirement, @Nullable String language) {

    public boolean hasRequirement() {
        return requirement != null && !requirement.isBlank();
    }
}
