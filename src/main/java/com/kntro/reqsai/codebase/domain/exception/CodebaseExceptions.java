package com.kntro.reqsai.codebase.domain.exception;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.shared.domain.exception.EntityNotFoundException;

/** Factory for Codebase domain exceptions. Messages are in English; the client translates by code. */
public final class CodebaseExceptions {

    private CodebaseExceptions() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static DomainException urlInvalid(String input) {
        return new DomainException(CodebaseError.CODE_REPOSITORY_URL_INVALID,
                "'%s' is not a GitHub repository: use owner/name or a github.com URL".formatted(input));
    }

    /** Unknown repository or branch, a private repository ReqsAI cannot read, or an id of another project. */
    public static EntityNotFoundException notFound(String what) {
        return new EntityNotFoundException(CodebaseError.CODE_REPOSITORY_NOT_FOUND,
                "Repository %s not found (or private and not shared with the ReqsAI GitHub App)".formatted(what));
    }

    public static DomainException accessDenied() {
        return new DomainException(CodebaseError.CODE_REPOSITORY_ACCESS_DENIED,
                "The code host refused access to the repository");
    }

    public static DomainException appNotConfigured() {
        return new DomainException(CodebaseError.CODE_HOST_APP_NOT_CONFIGURED,
                "The GitHub App is not configured on this server");
    }

    public static DomainException installStateInvalid(String detail) {
        return new DomainException(CodebaseError.CODE_HOST_INSTALL_STATE_INVALID,
                "The GitHub installation link is invalid or expired: " + detail);
    }

    public static DomainException installationForbidden(String detail) {
        return new DomainException(CodebaseError.CODE_HOST_INSTALLATION_FORBIDDEN,
                "The GitHub installation cannot be linked: " + detail);
    }

    public static EntityNotFoundException installationNotFound(long installationId) {
        return new EntityNotFoundException(CodebaseError.CODE_HOST_INSTALLATION_NOT_FOUND,
                "GitHub installation %d not found".formatted(installationId));
    }

    public static DomainException alreadyConnected(String fullName) {
        return new DomainException(CodebaseError.CODE_REPOSITORY_ALREADY_CONNECTED,
                "Repository %s is already connected to this project".formatted(fullName));
    }

    public static DomainException limitReached(int max) {
        return new DomainException(CodebaseError.CODE_REPOSITORY_LIMIT_REACHED,
                "A project connects at most %d repositories".formatted(max));
    }

    public static DomainException indexing() {
        return new DomainException(CodebaseError.CODE_REPOSITORY_INDEXING,
                "The repository is already being indexed");
    }

    public static DomainException tooLarge(String detail) {
        return new DomainException(CodebaseError.CODE_REPOSITORY_TOO_LARGE,
                "The repository is too large to index: " + detail);
    }

    public static DomainException hostUnavailable(String detail) {
        return new DomainException(CodebaseError.CODE_HOST_UNAVAILABLE,
                "The code host is unavailable: " + detail);
    }
}
