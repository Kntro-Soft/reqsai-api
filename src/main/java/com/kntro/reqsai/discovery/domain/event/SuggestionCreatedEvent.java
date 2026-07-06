package com.kntro.reqsai.discovery.domain.event;

import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.SuggestionType;
import com.kntro.reqsai.shared.domain.model.DomainEvent;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SuggestionCreatedEvent(
        UUID suggestionId,
        UUID sessionId,
        UUID projectId,
        SuggestionType type,
        @Nullable String draftTitle,
        @Nullable String draftRole,
        @Nullable String draftAction,
        @Nullable String draftBenefit,
        @Nullable Priority draftPriority,
        @Nullable Integer draftStoryPoints,
        @Nullable String relatedTopic,
        @Nullable UUID targetStoryId,
        @Nullable String question,
        List<Suggestion.DraftCriterion> draftAcceptanceCriteria,
        Instant occurredAt
) implements DomainEvent {

    public static SuggestionCreatedEvent of(Suggestion s) {
        return new SuggestionCreatedEvent(
                s.getId(), s.getSessionId(), s.getProjectId(), s.getType(),
                s.getDraftTitle(), s.getDraftRole(), s.getDraftAction(), s.getDraftBenefit(),
                s.getDraftPriority(), s.getDraftStoryPoints(),
                s.getRelatedTopic(), s.getTargetStoryId(), s.getQuestion(),
                s.getDraftAcceptanceCriteria(),
                Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return suggestionId;
    }
}
