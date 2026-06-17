package com.kntro.reqsai.discovery.domain.exception;

import com.kntro.reqsai.discovery.domain.model.SessionStatus;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.shared.domain.exception.EntityNotFoundException;

/**
 * Factory for Requirement Discovery domain exceptions — the context-specific counterpart of the shared
 * {@code Exceptions} and a mirror of {@code WorkspaceExceptions}.
 */
public final class DiscoveryExceptions {

    private DiscoveryExceptions() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static DomainException duplicateUserStory(double similarity) {
        return new DomainException(DiscoveryError.DUPLICATE_USER_STORY,
                "A near-duplicate user story already exists (similarity %.2f)".formatted(similarity));
    }

    public static DomainException invalidTransition(Object currentStatus, String operation) {
        return new DomainException(DiscoveryError.INVALID_SESSION_TRANSITION,
                "Cannot perform '%s' when session is in status '%s'".formatted(operation, currentStatus));
    }

    public static EntityNotFoundException sessionNotFound(java.util.UUID id) {
        return new EntityNotFoundException(DiscoveryError.SESSION_NOT_FOUND,
                "Discovery session '%s' not found".formatted(id));
    }

    public static DomainException invalidSessionStatus(SessionStatus current, SessionStatus required) {
        return new DomainException(DiscoveryError.INVALID_SESSION_STATUS,
                "Operation requires status %s but session is %s".formatted(required, current));
    }

    public static EntityNotFoundException userStoryNotFound(java.util.UUID id) {
        return new EntityNotFoundException(DiscoveryError.USER_STORY_NOT_FOUND,
                "User story '%s' not found".formatted(id));
    }

    public static DomainException requirementGenerationFailed(String reason) {
        return new DomainException(DiscoveryError.REQUIREMENT_GENERATION_FAILED,
                "Requirement generation failed: " + reason);
    }
}
