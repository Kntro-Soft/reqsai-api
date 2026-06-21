package com.kntro.reqsai.discovery.application.command;

import com.kntro.reqsai.discovery.domain.model.Priority;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Analyst accepts a pending suggestion.
 *
 * <p>All {@code edited*} fields are optional overrides. When null, the suggestion's original draft
 * values are used without modification.
 *
 * @param sessionId    the session that owns the suggestion
 * @param suggestionId the suggestion being accepted
 * @param editedTitle  optional analyst override for the draft title
 * @param editedRole   optional override
 * @param editedAction optional override
 * @param editedBenefit optional override
 * @param editedPriority optional override
 * @param editedStoryPoints optional override
 */
public record AcceptSuggestionCommand(
        UUID sessionId,
        UUID suggestionId,
        @Nullable String editedTitle,
        @Nullable String editedRole,
        @Nullable String editedAction,
        @Nullable String editedBenefit,
        @Nullable Priority editedPriority,
        @Nullable Integer editedStoryPoints
) {
}
