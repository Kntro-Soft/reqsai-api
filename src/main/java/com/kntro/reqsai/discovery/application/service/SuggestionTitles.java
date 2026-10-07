package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.GenerationResult;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Picks the title of each {@code NEW_STORY} suggestion of one realtime pass, so that accepting the
 * suggestions in the analyst's queue never creates two stories with the same title.
 *
 * <ul>
 *   <li>A draft the model linked to a PENDING suggestion and that is kept as a {@code NEW_STORY}
 *       ({@link #forLinkedDraft}) usually carries the linked suggestion's title: the model copies it
 *       into the {@code UPDATE_STORY} or {@code EDGE_CASE} it meant to emit. Its own title is kept only
 *       when it names something the linked title does not.</li>
 *   <li>A {@code NEW_STORY} whose title is already in the queue ({@link #uniqueNewStoryTitle}) gets a
 *       title of its own.</li>
 * </ul>
 * In both cases the new title comes from the draft's own content, in this order: a criterion's scenario
 * label, the action, a criterion's Then (the rule's outcome), When or Given, the benefit, and the action
 * qualified by the role. The first one that says something the other draft does not, and is not taken
 * yet, wins. A numbered title is the last resort. The choice is deterministic: the same drafts in the
 * same order always get the same titles.
 */
final class SuggestionTitles {

    /** A title derived from the draft's content is cut at a word boundary to at most this many characters. */
    static final int DERIVED_TITLE_MAX = 120;

    /** Story titles already in the queue (normalized), each with the draft that holds it. */
    private final Map<String, SuggestionDedupPolicy.Draft> taken = new HashMap<>();

    /** Records the title of a story suggestion already in the queue: pending, or kept earlier in this pass. */
    void reserve(SuggestionDedupPolicy.Draft draft) {
        String key = SuggestionDedupPolicy.normalize(draft.title());
        if (key != null) {
            taken.putIfAbsent(key, draft);
        }
    }

    /**
     * The title of a draft linked to the pending suggestion {@code linked} and kept as a {@code NEW_STORY}.
     * Never the linked suggestion's title: the draft's own title when it names something the linked one
     * does not, otherwise a title derived from the draft's own content.
     */
    String forLinkedDraft(GenerationResult.GeneratedStory gen, SuggestionDedupPolicy.Draft linked) {
        if (!reusesTitle(gen.title(), linked.title())) {
            return gen.title();
        }
        String derived = derived(gen, linked);
        if (derived != null) {
            return derived;
        }
        return numbered(Objects.requireNonNullElse(asTitle(gen.action()), gen.title()), linked.title());
    }

    /**
     * The title for a {@code NEW_STORY} built from {@code gen}: its own title when no story in the queue
     * has it, otherwise a title derived from its own content. Does not reserve it; {@link #reserve} the
     * kept suggestion.
     */
    String uniqueNewStoryTitle(GenerationResult.GeneratedStory gen) {
        String key = SuggestionDedupPolicy.normalize(gen.title());
        SuggestionDedupPolicy.Draft holder = key == null ? null : taken.get(key);
        if (holder == null) {
            return gen.title();
        }
        String derived = derived(gen, holder);
        return derived != null ? derived : numbered(gen.title(), null);
    }

    /**
     * True when {@code title} names nothing {@code linkedTitle} does not: every content word of it is in
     * the linked title ("Reservar cita médica" for "Reserva de cita médica"), or it has no content word.
     */
    static boolean reusesTitle(@Nullable String title, @Nullable String linkedTitle) {
        var words = SuggestionDedupPolicy.tokens(title);
        return words.isEmpty() || SuggestionDedupPolicy.tokens(linkedTitle).containsAll(words);
    }

    /**
     * {@code text} as a title: single-spaced, without surrounding quotes or trailing punctuation, first
     * letter upper-cased, cut at a word boundary to {@link #DERIVED_TITLE_MAX}; {@code null} when blank.
     */
    static @Nullable String asTitle(@Nullable String text) {
        if (text == null) {
            return null;
        }
        String t = trimPunctuation(text.strip().replaceAll("\\s+", " "));
        if (t.length() > DERIVED_TITLE_MAX) {
            int cut = t.lastIndexOf(' ', DERIVED_TITLE_MAX);
            t = trimPunctuation(cut > 0 ? t.substring(0, cut) : t.substring(0, DERIVED_TITLE_MAX));
        }
        if (t.isEmpty()) {
            return null;
        }
        return t.substring(0, 1).toUpperCase(Locale.ROOT) + t.substring(1);
    }

    /**
     * The first content-derived title of {@code gen} that says something {@code other} does not (a content
     * word missing from its title, role, action, benefit and criteria) and that no story in the queue has.
     */
    private @Nullable String derived(GenerationResult.GeneratedStory gen, SuggestionDedupPolicy.Draft other) {
        var otherWords = SuggestionDedupPolicy.tokens(other.fullText());
        for (String candidate : candidates(gen)) {
            String title = asTitle(candidate);
            if (title == null || otherWords.containsAll(SuggestionDedupPolicy.tokens(title))) {
                continue;
            }
            if (!taken.containsKey(SuggestionDedupPolicy.normalize(title))) {
                return title;
            }
        }
        return null;
    }

    /** The draft's own texts a title can come from, in order of preference. */
    private static List<String> candidates(GenerationResult.GeneratedStory gen) {
        List<GenerationResult.GeneratedCriterion> criteria = gen.acceptanceCriteria() == null ? List.of()
                : gen.acceptanceCriteria().stream().filter(Objects::nonNull).toList();
        List<String> out = new ArrayList<>();
        criteria.forEach(c -> out.add(c.scenario()));
        out.add(gen.action());
        criteria.forEach(c -> out.add(c.then()));
        criteria.forEach(c -> out.add(c.when()));
        criteria.forEach(c -> out.add(c.given()));
        out.add(gen.benefit());
        if (gen.action() != null && gen.role() != null) {
            out.add("%s (%s)".formatted(gen.action().strip(), gen.role().strip()));
        }
        return out;
    }

    /** {@code base} itself, or with the first free number appended: "base (2)", "base (3)", … */
    private String numbered(String base, @Nullable String avoid) {
        String avoidKey = SuggestionDedupPolicy.normalize(avoid);
        String title = base;
        for (int n = 2; isTaken(title) || Objects.equals(SuggestionDedupPolicy.normalize(title), avoidKey); n++) {
            title = "%s (%d)".formatted(base, n);
        }
        return title;
    }

    private boolean isTaken(String title) {
        String key = SuggestionDedupPolicy.normalize(title);
        return key != null && taken.containsKey(key);
    }

    private static String trimPunctuation(String text) {
        return text.replaceAll("^[\\s\"'“”«»¿¡\\-–—]+", "").replaceAll("[\\s\"'“”«».,;:!?\\-–—]+$", "");
    }
}
