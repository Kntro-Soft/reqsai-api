package com.kntro.reqsai.discovery.interfaces.notification.mappers;

import com.kntro.reqsai.discovery.domain.event.UserStoryCreatedEvent;
import com.kntro.reqsai.discovery.interfaces.notification.SessionEventType;
import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionStoryGeneratedMessage;

import java.util.Objects;
import java.util.UUID;

/**
 * Maps {@link com.kntro.reqsai.discovery.domain.model.UserStory} domain events to their
 * corresponding WebSocket payload messages.
 *
 */
public final class UserStoryNotificationMapper {

    private UserStoryNotificationMapper() {
    }

    /**
     * {@link SessionEventType#STORY_GENERATED} — a story was generated from the session.
     * Maps all story fields so the client can render the story immediately without a follow-up GET.
     *
     * @throws NullPointerException if {@code event.sessionId()} is null — only call for session-scoped stories
     */
    public static SessionStoryGeneratedMessage toMessage(UserStoryCreatedEvent event) {
        UUID sessionId = Objects.requireNonNull(event.sessionId(), "sessionId must be set for session-scoped story notifications");
        return new SessionStoryGeneratedMessage(
                sessionId,
                event.storyId(),
                event.title(),
                event.role(),
                event.action(),
                event.benefit(),
                event.priority(),
                event.storyPoints(),
                event.occurredAt()
        );
    }
}
