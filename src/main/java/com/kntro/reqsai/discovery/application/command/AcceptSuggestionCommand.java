package com.kntro.reqsai.discovery.application.command;

import com.kntro.reqsai.discovery.domain.model.Priority;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Analyst accepts a pending suggestion, optionally editing the whole draft before it is committed to
 * the backlog.
 *
 * <p>All {@code edited*} fields are optional overrides. When {@code null} (or blank, for text), the
 * suggestion's original draft value is used. What each field affects depends on the suggestion type:
 * <ul>
 *   <li>{@code NEW_STORY} — title/role/action/benefit/priority/storyPoints and, when
 *       {@code editedAcceptanceCriteria} is present, the full replacement list of criteria.</li>
 *   <li>{@code EDGE_CASE} — the criterion added to the target story: the first entry of
 *       {@code editedAcceptanceCriteria} when present, else the draft criterion.</li>
 *   <li>{@code UPDATE_STORY} — title/role/action/benefit/priority/storyPoints applied to the target
 *       story.</li>
 * </ul>
 *
 * @param sessionId                the session that owns the suggestion; {@code null} when the suggestion is
 *                                 looked up by its project (assistant chat)
 * @param suggestionId             the suggestion being accepted
 * @param editedTitle              optional analyst override for the draft title
 * @param editedRole               optional override
 * @param editedAction             optional override
 * @param editedBenefit            optional override
 * @param editedPriority           optional override
 * @param editedStoryPoints        optional override
 * @param editedAcceptanceCriteria optional full replacement of the acceptance criteria; {@code null}
 *                                 (absent) keeps the draft criteria
 * @param approve                  {@code true} approves the resulting story in the same transaction; {@code false}
 *                                 saves it as a draft (a target story already in review goes back to draft)
 */
public record AcceptSuggestionCommand(
        @Nullable UUID sessionId,
        UUID suggestionId,
        @Nullable String editedTitle,
        @Nullable String editedRole,
        @Nullable String editedAction,
        @Nullable String editedBenefit,
        @Nullable Priority editedPriority,
        @Nullable Integer editedStoryPoints,
        @Nullable List<Criterion> editedAcceptanceCriteria,
        boolean approve
) {

    /** Accepts as a draft (no approval), the behaviour before the approve flag existed. */
    public AcceptSuggestionCommand(@Nullable UUID sessionId, UUID suggestionId,
                                   @Nullable String editedTitle, @Nullable String editedRole,
                                   @Nullable String editedAction, @Nullable String editedBenefit,
                                   @Nullable Priority editedPriority, @Nullable Integer editedStoryPoints,
                                   @Nullable List<Criterion> editedAcceptanceCriteria) {
        this(sessionId, suggestionId, editedTitle, editedRole, editedAction, editedBenefit,
                editedPriority, editedStoryPoints, editedAcceptanceCriteria, false);
    }

    /** An edited acceptance criterion in Given/When/Then form ({@code scenario} is an optional label). */
    public record Criterion(
            @Nullable String scenario,
            String given,
            String when,
            String then
    ) {}
}
