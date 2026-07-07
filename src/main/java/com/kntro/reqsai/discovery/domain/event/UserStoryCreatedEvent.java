package com.kntro.reqsai.discovery.domain.event;

import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.shared.domain.model.DomainEvent;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a user story is created (in {@code DRAFT}), whether manually or AI-generated.
 *
 * <p>{@code sessionId} is the originating discovery session for AI-generated stories and
 * {@code null} for manually created ones — it lets the realtime listener route a generated
 * story to its session topic while ignoring manual creations (no live session page to update).
 *
 * <p>Story fields are included in the event so downstream consumers (e.g. WebSocket notifiers)
 * can push a complete payload to the client without a follow-up query.
 */
public record UserStoryCreatedEvent(
        UUID storyId,
        @Nullable UUID sessionId,
        UUID projectId,
        String title,
        String role,
        String action,
        String benefit,
        Priority priority,
        @Nullable Integer storyPoints,
        Instant occurredAt
) implements DomainEvent {

    public static UserStoryCreatedEvent of(
            UUID storyId,
            @Nullable UUID sessionId,
            UUID projectId,
            String title,
            String role,
            String action,
            String benefit,
            Priority priority,
            @Nullable Integer storyPoints
    ) {
        return new UserStoryCreatedEvent(storyId, sessionId, projectId, title, role, action, benefit, priority, storyPoints, Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return storyId;
    }
}
