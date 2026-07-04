package com.kntro.reqsai.discovery.domain.exception;

import com.kntro.reqsai.discovery.domain.model.SuggestionStatus;
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

    public static EntityNotFoundException sessionNotFound(java.util.UUID id) {
        return new EntityNotFoundException(DiscoveryError.SESSION_NOT_FOUND,
                "Discovery session '%s' not found".formatted(id));
    }

    public static EntityNotFoundException userStoryNotFound(java.util.UUID id) {
        return new EntityNotFoundException(DiscoveryError.USER_STORY_NOT_FOUND,
                "User story '%s' not found".formatted(id));
    }

    public static EntityNotFoundException acceptanceCriterionNotFound(java.util.UUID id) {
        return new EntityNotFoundException(DiscoveryError.ACCEPTANCE_CRITERION_NOT_FOUND,
                "Acceptance criterion '%s' not found on this story".formatted(id));
    }

    public static DomainException requirementGenerationFailed(String reason) {
        return new DomainException(DiscoveryError.REQUIREMENT_GENERATION_FAILED,
                "Requirement generation failed: " + reason);
    }

    public static EntityNotFoundException suggestionNotFound(java.util.UUID id) {
        return new EntityNotFoundException(DiscoveryError.SUGGESTION_NOT_FOUND,
                "Suggestion '%s' not found".formatted(id));
    }

    public static DomainException suggestionAlreadyResolved(java.util.UUID id, SuggestionStatus status) {
        return new DomainException(DiscoveryError.SUGGESTION_ALREADY_RESOLVED,
                "Suggestion '%s' is already %s".formatted(id, status));
    }

    public static DomainException sessionAccessDenied(java.util.UUID sessionId, java.util.UUID userId) {
        return new DomainException(DiscoveryError.SESSION_ACCESS_DENIED,
                "User '%s' may not stream audio into session '%s'".formatted(userId, sessionId));
    }
}
