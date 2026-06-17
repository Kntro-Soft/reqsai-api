package com.kntro.reqsai.discovery.domain.event;

import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.shared.domain.model.DomainEvent;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when AI generation produces a story whose embedding is too similar to an existing one.
 * The candidate is not persisted, but the event is published so a future interactive flow can
 * surface it to the user as an update/merge suggestion.
 */
public record UserStoryNearDuplicateDetectedEvent(
        UUID sessionId,
        UUID projectId,
        String candidateTitle,
        String candidateRole,
        String candidateAction,
        String candidateBenefit,
        Priority candidatePriority,
        @Nullable Integer candidateStoryPoints,
        Instant occurredAt
) implements DomainEvent {

    public static UserStoryNearDuplicateDetectedEvent of(UUID sessionId, UUID projectId, String title, String role, String action, String benefit, Priority priority, @Nullable Integer storyPoints) {
        return new UserStoryNearDuplicateDetectedEvent(sessionId, projectId, title, role, action, benefit, priority, storyPoints, Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return sessionId;
    }
}
