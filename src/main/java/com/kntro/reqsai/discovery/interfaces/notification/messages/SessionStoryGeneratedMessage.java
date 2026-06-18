package com.kntro.reqsai.discovery.interfaces.notification.messages;

import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.interfaces.notification.SessionEventType;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * WebSocket payload for {@link SessionEventType#STORY_GENERATED} — a user story was generated
 * and persisted. Carries the full story fields so the client can render it immediately without
 * a follow-up REST call.
 *
 * <p>Emitted once per story so the client can append stories to the live backlog incrementally
 * instead of waiting for a bulk push at completion.
 */
public record SessionStoryGeneratedMessage(
        UUID sessionId,
        UUID storyId,
        String title,
        String role,
        String action,
        String benefit,
        Priority priority,
        @Nullable Integer storyPoints,
        Instant occurredAt
) implements SessionRealtimeMessage {

    @Override
    public SessionEventType type() {
        return SessionEventType.STORY_GENERATED;
    }
}
