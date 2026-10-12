package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.SuggestionType;

import java.util.ArrayList;
import java.util.List;

/**
 * The text a suggestion is verified against the code with: the story it proposes (title, role, action, benefit),
 * its criteria (they hold the values a conflict is judged on) and the fragment of the conversation it came from;
 * for a clarifying question, the question.
 */
public final class SuggestionCodeSubject {

    private static final int MAX_CHARS = 2000;

    private SuggestionCodeSubject() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static String of(Suggestion s) {
        List<String> parts = new ArrayList<>();
        if (s.getType() == SuggestionType.CLARIFYING_QUESTION) {
            add(parts, s.getQuestion());
        } else {
            add(parts, s.getDraftTitle());
            String story = join(" ", label("Role", s.getDraftRole()), label("Action", s.getDraftAction()),
                    label("Benefit", s.getDraftBenefit()));
            add(parts, story);
            for (Suggestion.DraftCriterion c : s.getDraftAcceptanceCriteria()) {
                add(parts, "Given " + c.given() + ", when " + c.when() + ", then " + c.then());
            }
        }
        add(parts, s.getEvidenceQuote() == null ? null : "Said in the conversation: " + s.getEvidenceQuote());
        String text = String.join("\n", parts);
        return text.length() <= MAX_CHARS ? text : text.substring(0, MAX_CHARS);
    }

    private static String label(String name, String value) {
        return value == null || value.isBlank() ? "" : name + ": " + value.strip() + ".";
    }

    private static String join(String separator, String... values) {
        List<String> kept = new ArrayList<>();
        for (String v : values) if (v != null && !v.isBlank()) kept.add(v);
        return String.join(separator, kept);
    }

    private static void add(List<String> parts, String value) {
        if (value != null && !value.isBlank()) parts.add(value.strip());
    }
}
