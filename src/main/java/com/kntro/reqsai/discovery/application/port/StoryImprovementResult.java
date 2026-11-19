package com.kntro.reqsai.discovery.application.port;

import java.util.List;

/**
 * What the model proposes for a story.
 *
 * @param proposal      the improved story; a value the source does not state is a {@code [[...]]} token
 * @param pendingValues the tokens of {@code proposal}, each with the question that tells the analyst what to supply
 * @param fixes         one short sentence per change the model made
 */
public record StoryImprovementResult(
        StoryDraft proposal,
        List<PendingValue> pendingValues,
        List<String> fixes
) {

    public StoryImprovementResult {
        pendingValues = pendingValues == null ? List.of() : List.copyOf(pendingValues);
        fixes = fixes == null ? List.of() : List.copyOf(fixes);
    }

    /** A value the analyst must supply: the literal {@code [[...]]} placeholder and a question that explains it. */
    public record PendingValue(String token, String hint) {
    }
}
