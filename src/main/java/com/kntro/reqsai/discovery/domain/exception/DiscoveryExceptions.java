package com.kntro.reqsai.discovery.domain.exception;

import com.kntro.reqsai.shared.domain.exception.DomainException;

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
}
