package com.kntro.reqsai.discovery.domain.model;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

/**
 * The text of a story as one string: title, role, action and benefit, one per line, followed by its acceptance
 * criteria (one line each, {@code scenario: given | when | then}, sorted so that reordering them is not a change).
 * <p>
 * The same composition freezes what the AI proposed ({@link UserStory#getGeneratedText()}) and reads what the story
 * says now ({@link UserStory#currentText()}), so the two can be compared (US71).
 */
public final class StoryText {

    private StoryText() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /** The text the story says now. */
    public static String of(UserStory story) {
        return compose(story.getTitle(), story.getRole(), story.getAction(), story.getBenefit(),
                story.getAcceptanceCriteria().stream()
                        .map(c -> criterion(c.getScenario(), c.getGiven(), c.getWhen(), c.getThen())).toList());
    }

    /** The text a suggestion proposes, with the criteria it carries at this moment. */
    public static String of(Suggestion suggestion) {
        return compose(suggestion.getDraftTitle(), suggestion.getDraftRole(), suggestion.getDraftAction(),
                suggestion.getDraftBenefit(),
                suggestion.getDraftAcceptanceCriteria().stream()
                        .map(c -> criterion(c.scenario(), c.given(), c.when(), c.then())).toList());
    }

    static String compose(@Nullable String title, @Nullable String role, @Nullable String action,
                          @Nullable String benefit, List<String> criteria) {
        return Stream.concat(Stream.of(title, role, action, benefit).map(StoryText::clean),
                criteria.stream().sorted()).reduce((a, b) -> a + "\n" + b).orElse("");
    }

    private static String criterion(@Nullable String scenario, @Nullable String given, @Nullable String when,
                                    @Nullable String then) {
        String body = clean(given) + " | " + clean(when) + " | " + clean(then);
        String label = clean(scenario);
        return label.isEmpty() ? body : label + ": " + body;
    }

    private static String clean(@Nullable String value) {
        return value == null ? "" : value.strip();
    }
}
