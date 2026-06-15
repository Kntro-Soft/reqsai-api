package com.kntro.reqsai.discovery.domain.event;

import com.kntro.reqsai.shared.domain.model.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/** Raised when a user story is created (in {@code DRAFT}), whether manually or AI-generated. */
public record UserStoryCreatedEvent(UUID storyId, UUID projectId, Instant occurredAt)
        implements DomainEvent {

    public static UserStoryCreatedEvent of(UUID storyId, UUID projectId) {
        return new UserStoryCreatedEvent(storyId, projectId, Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return storyId;
    }
}
