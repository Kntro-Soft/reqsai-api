package com.kntro.reqsai.workspace.application.service;

import com.kntro.reqsai.workspace.application.port.DocumentClassification;
import com.kntro.reqsai.workspace.application.port.DocumentClassification.SuggestedTerm;
import com.kntro.reqsai.workspace.domain.model.ProjectDocument;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure rules that turn a raw document classification into what the analyst reviews: trimmed text,
 * case-insensitive duplicates removed, lengths kept within what the glossary and constraint forms
 * accept, and list sizes capped. Also caps the text sent to the model and derives a fallback summary
 * when no model classified the document.
 */
public final class ClientDocumentSuggestions {

    /** Characters of document text sent to the model (about 15k tokens); the rest is not classified. */
    public static final int MODEL_INPUT_MAX_CHARS = 60_000;
    static final int MAX_TERMS = 40;
    static final int MAX_CONSTRAINTS = 30;
    static final int TERM_MAX = 200;
    static final int DEFINITION_MAX = 1000;
    /** The constraints page accepts up to 500 characters, so suggestions stay editable there. */
    static final int CONSTRAINT_MAX = 500;
    static final int FALLBACK_SUMMARY_MAX = 1000;

    private ClientDocumentSuggestions() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /** Normalizes the model output for review; never returns {@code null} lists. */
    public static DocumentClassification normalize(DocumentClassification raw) {
        String summary = clean(raw.summary());
        if (summary != null) {
            summary = cut(summary, ProjectDocument.SUMMARY_MAX);
        }

        Map<String, SuggestedTerm> terms = new LinkedHashMap<>();
        for (SuggestedTerm suggested : raw.glossary()) {
            if (suggested == null) continue;
            String term = clean(suggested.term());
            String definition = clean(suggested.definition());
            if (term == null || definition == null || term.length() > TERM_MAX) continue;
            terms.putIfAbsent(key(term), new SuggestedTerm(term, cut(definition, DEFINITION_MAX)));
            if (terms.size() == MAX_TERMS) break;
        }

        Map<String, String> constraints = new LinkedHashMap<>();
        for (String suggested : raw.constraints()) {
            String constraint = clean(suggested);
            if (constraint == null) continue;
            constraint = cut(constraint, CONSTRAINT_MAX);
            constraints.putIfAbsent(key(constraint), constraint);
            if (constraints.size() == MAX_CONSTRAINTS) break;
        }

        return new DocumentClassification(summary, raw.documentType(),
                new ArrayList<>(terms.values()), new ArrayList<>(constraints.values()));
    }

    /** The beginning of the text the model reads, cut on a word boundary. */
    public static String modelInput(String text) {
        return text.length() <= MODEL_INPUT_MAX_CHARS ? text : cutAtWord(text, MODEL_INPUT_MAX_CHARS);
    }

    /** An excerpt of the document used as its context when no model summarized it. */
    public static String fallbackSummary(String text) {
        String flat = text.replaceAll("\\s+", " ").strip();
        return flat.length() <= FALLBACK_SUMMARY_MAX ? flat : cutAtWord(flat, FALLBACK_SUMMARY_MAX - 1) + "…";
    }

    /** Case- and space-insensitive identity used for duplicate detection. */
    public static String key(String value) {
        return value.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static @Nullable String clean(@Nullable String value) {
        if (value == null) return null;
        String cleaned = value.replaceAll("\\s+", " ").strip();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : cutAtWord(value, max - 1) + "…";
    }

    private static String cutAtWord(String value, int max) {
        String head = value.substring(0, max);
        if (Character.isHighSurrogate(head.charAt(head.length() - 1))) {
            head = head.substring(0, head.length() - 1);
        }
        int space = head.length() - 1;
        while (space > 0 && !Character.isWhitespace(head.charAt(space))) {
            space--;
        }
        return (space > max / 2 ? head.substring(0, space) : head).strip();
    }
}
