package com.kntro.reqsai.workspace.application.port;

import com.kntro.reqsai.workspace.domain.model.DocumentType;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The classification of a client document.
 *
 * @param summary      the project context the document gives, in a few sentences
 * @param documentType the kind of document, when the model could tell
 * @param glossary     domain terms the document defines or uses with a specific meaning
 * @param constraints  conditions the project must respect (legal, technical, business, schedule…)
 */
public record DocumentClassification(
        @Nullable String summary,
        @Nullable DocumentType documentType,
        List<SuggestedTerm> glossary,
        List<String> constraints
) {

    public DocumentClassification {
        glossary = glossary == null ? List.of() : List.copyOf(glossary);
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
    }

    /** A classification with nothing in it, used when no model could classify the document. */
    public static DocumentClassification empty() {
        return new DocumentClassification(null, null, List.of(), List.of());
    }

    /** A glossary term proposed by the model. */
    public record SuggestedTerm(String term, String definition) {}
}
