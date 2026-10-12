package com.kntro.reqsai.discovery.interfaces.notification.mappers;

import com.kntro.reqsai.discovery.domain.event.SuggestionAcceptedEvent;
import com.kntro.reqsai.discovery.domain.event.SuggestionCreatedEvent;
import com.kntro.reqsai.discovery.domain.event.SuggestionDismissedEvent;
import com.kntro.reqsai.discovery.domain.model.SuggestionStatus;
import com.kntro.reqsai.discovery.interfaces.notification.SessionEventType;
import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionSuggestionMessage;
import com.kntro.reqsai.discovery.interfaces.rest.mappers.response.InsightResponseMapper;

public final class SuggestionNotificationMapper {

    private SuggestionNotificationMapper() {}

    public static SessionSuggestionMessage toGeneratedMessage(SuggestionCreatedEvent e) {
        return new SessionSuggestionMessage(
                e.sessionId(), e.suggestionId(),
                SessionEventType.SUGGESTION_GENERATED, e.type(), SuggestionStatus.PENDING,
                e.draftTitle(), e.draftRole(), e.draftAction(), e.draftBenefit(),
                e.draftPriority(), e.draftStoryPoints(), e.relatedTopic(),
                e.targetStoryId(), e.question(), e.draftAcceptanceCriteria(), null, e.occurredAt(),
                InsightResponseMapper.evidence(e.evidenceSequence(), e.evidenceQuote()),
                InsightResponseMapper.code(e.codeFinding(), e.codeNote(), e.codeReferences()));
    }

    public static SessionSuggestionMessage toAcceptedMessage(SuggestionAcceptedEvent e) {
        return new SessionSuggestionMessage(
                e.sessionId(), e.suggestionId(),
                SessionEventType.SUGGESTION_ACCEPTED, e.type(), SuggestionStatus.ACCEPTED,
                e.draftTitle(), e.draftRole(), e.draftAction(), e.draftBenefit(),
                e.draftPriority(), e.draftStoryPoints(), e.relatedTopic(),
                e.targetStoryId(), e.question(), e.draftAcceptanceCriteria(),
                e.resolvedStoryId(), e.occurredAt(),
                InsightResponseMapper.evidence(e.evidenceSequence(), e.evidenceQuote()),
                InsightResponseMapper.code(e.codeFinding(), e.codeNote(), e.codeReferences()));
    }

    public static SessionSuggestionMessage toDismissedMessage(SuggestionDismissedEvent e) {
        return new SessionSuggestionMessage(
                e.sessionId(), e.suggestionId(),
                SessionEventType.SUGGESTION_DISMISSED, e.type(), SuggestionStatus.DISMISSED,
                e.draftTitle(), e.draftRole(), e.draftAction(), e.draftBenefit(),
                e.draftPriority(), e.draftStoryPoints(), e.relatedTopic(),
                e.targetStoryId(), e.question(), java.util.List.of(),
                null, e.occurredAt(),
                InsightResponseMapper.evidence(e.evidenceSequence(), e.evidenceQuote()),
                InsightResponseMapper.code(e.codeFinding(), e.codeNote(), e.codeReferences()));
    }
}
