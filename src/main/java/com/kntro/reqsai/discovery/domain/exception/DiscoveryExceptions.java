package com.kntro.reqsai.discovery.domain.exception;

import com.kntro.reqsai.discovery.domain.model.SuggestionStatus;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.shared.domain.exception.EntityNotFoundException;

import java.util.Locale;

/**
 * Factory for Requirement Discovery domain exceptions — the context-specific counterpart of the shared
 * {@code Exceptions} and a mirror of {@code WorkspaceExceptions}.
 */
public final class DiscoveryExceptions {

    private DiscoveryExceptions() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static DomainException duplicateUserStory(double similarity) {
        // Format with Locale.ROOT so the decimal separator is always a dot: the client parses this
        // similarity out of the ProblemDetail `detail` expecting "0.87", and a comma-decimal JVM locale
        // would otherwise make it read the score as 0 (0%) on the duplicate-story surface.
        return new DomainException(DiscoveryError.DUPLICATE_USER_STORY,
                String.format(Locale.ROOT, "A near-duplicate user story already exists (similarity %.2f)", similarity));
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

    public static DomainException edgeCaseWithoutTarget(java.util.UUID id) {
        return new DomainException(DiscoveryError.EDGE_CASE_WITHOUT_TARGET,
                ("Edge-case suggestion '%s' has no resolvable target story; assign a target story (edit "
                        + "it or accept it as a new story explicitly) before accepting it as an edge case")
                        .formatted(id));
    }

    public static DomainException sessionAccessDenied(java.util.UUID sessionId, java.util.UUID userId) {
        return new DomainException(DiscoveryError.SESSION_ACCESS_DENIED,
                "User '%s' may not stream audio into session '%s'".formatted(userId, sessionId));
    }

    public static DomainException sessionAlreadyActive(java.util.UUID projectId, java.util.UUID activeSessionId) {
        return new DomainException(DiscoveryError.SESSION_ALREADY_ACTIVE,
                "Project '%s' already has an active session '%s' (RECORDING or PAUSED); stop it before starting another"
                        .formatted(projectId, activeSessionId));
    }
}
