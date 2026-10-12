package com.kntro.reqsai.discovery.application.port;

import com.kntro.reqsai.discovery.domain.model.CodeFinding;
import com.kntro.reqsai.discovery.domain.model.CodeReference;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * What the model adds to a generated story beyond the story itself: the verbatim quote of the conversation it
 * is based on, and what the client's code says about it (already built or in conflict, with the modules).
 */
public record StoryInsight(
        @Nullable String evidenceQuote,
        @Nullable CodeFinding codeFinding,
        @Nullable String codeNote,
        List<CodeReference> codeReferences
) {

    public StoryInsight {
        codeReferences = codeReferences == null ? List.of() : List.copyOf(codeReferences);
    }

    public static StoryInsight evidence(@Nullable String quote) {
        return new StoryInsight(quote, null, null, List.of());
    }

    public boolean isEmpty() {
        return (evidenceQuote == null || evidenceQuote.isBlank()) && codeFinding == null && codeReferences.isEmpty();
    }
}
