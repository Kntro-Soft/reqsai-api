package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.StoryDraft;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code [[...]]} placeholders of an improved story (US73). Where a measurable value is needed and the source
 * does not state it, the model writes a token such as {@code [[tiempo máximo de respuesta]]} instead of inventing
 * a number; the analyst replaces every token before the proposal can be applied. A token is two opening brackets,
 * anything but a bracket, and two closing brackets, so {@code [[]]} is a (malformed) token too.
 */
public final class PendingValues {

    private static final Pattern TOKEN = Pattern.compile("\\[\\[[^\\[\\]]*]]");
    private static final Pattern WRAPPED = Pattern.compile("\\[\\[[^\\[\\]]+]]");

    private PendingValues() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /** The distinct tokens in {@code text}, in order of appearance. */
    public static List<String> in(String text) {
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = TOKEN.matcher(text == null ? "" : text);
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return new ArrayList<>(found);
    }

    /** The distinct tokens in any text of the story (title, role, action, benefit and every criterion field). */
    public static List<String> in(StoryDraft draft) {
        Set<String> found = new LinkedHashSet<>();
        found.addAll(in(draft.title()));
        found.addAll(in(draft.role()));
        found.addAll(in(draft.action()));
        found.addAll(in(draft.benefit()));
        for (StoryDraft.CriterionDraft criterion : draft.acceptanceCriteria()) {
            found.addAll(in(criterion.scenario()));
            found.addAll(in(criterion.given()));
            found.addAll(in(criterion.when()));
            found.addAll(in(criterion.then()));
        }
        return new ArrayList<>(found);
    }

    /**
     * A token a model declared, in its literal form: a declaration written without brackets
     * ({@code tiempo máximo}) is wrapped; one that is empty or has stray brackets is not a token ({@code null}).
     */
    static String literal(String declared) {
        if (declared == null || declared.isBlank()) {
            return null;
        }
        String text = declared.strip();
        if (WRAPPED.matcher(text).matches()) {
            return text;
        }
        return text.contains("[") || text.contains("]") ? null : "[[" + text + "]]";
    }

    /** What is between the brackets of a token. */
    static String inner(String token) {
        return token.substring(2, token.length() - 2).strip();
    }
}
