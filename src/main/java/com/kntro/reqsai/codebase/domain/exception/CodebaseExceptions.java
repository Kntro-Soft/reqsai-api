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

    /** Unknown repository or branch, a private repository without a token, or an id of another project. */
    public static EntityNotFoundException notFound(String what) {
        return new EntityNotFoundException(CodebaseError.CODE_REPOSITORY_NOT_FOUND,
                "Repository %s not found (or private without an access token)".formatted(what));
    }

    public static DomainException accessDenied() {
        return new DomainException(CodebaseError.CODE_REPOSITORY_ACCESS_DENIED,
                "The access token was rejected by the code host");
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
