package com.kntro.reqsai.discovery.interfaces.notification.mappers;

import com.kntro.reqsai.discovery.domain.event.SuggestionAcceptedEvent;
import com.kntro.reqsai.discovery.domain.event.SuggestionCreatedEvent;
import com.kntro.reqsai.discovery.domain.event.SuggestionDismissedEvent;
import com.kntro.reqsai.discovery.domain.model.SuggestionStatus;
import com.kntro.reqsai.discovery.interfaces.notification.SessionEventType;
import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionSuggestionMessage;

public final class SuggestionNotificationMapper {

    private SuggestionNotificationMapper() {}

    public static SessionSuggestionMessage toGeneratedMessage(SuggestionCreatedEvent e) {
        return new SessionSuggestionMessage(
                e.sessionId(), e.suggestionId(),
                SessionEventType.SUGGESTION_GENERATED, e.type(), SuggestionStatus.PENDING,
                e.draftTitle(), e.draftRole(), e.draftAction(), e.draftBenefit(),
                e.draftPriority(), e.draftStoryPoints(), e.relatedTopic(),
                e.targetStoryId(), e.question(), null, e.occurredAt());
    }

    public static SessionSuggestionMessage toAcceptedMessage(SuggestionAcceptedEvent e) {
        return new SessionSuggestionMessage(
                e.sessionId(), e.suggestionId(),
                SessionEventType.SUGGESTION_ACCEPTED, e.type(), SuggestionStatus.ACCEPTED,
                null, null, null, null, null, null, null, null, null,
                e.resolvedStoryId(), e.occurredAt());
    }

    public static SessionSuggestionMessage toDismissedMessage(SuggestionDismissedEvent e) {
        return new SessionSuggestionMessage(
                e.sessionId(), e.suggestionId(),
                SessionEventType.SUGGESTION_DISMISSED, e.type(), SuggestionStatus.DISMISSED,
                null, null, null, null, null, null, null, null, null,
                null, e.occurredAt());
    }
}
