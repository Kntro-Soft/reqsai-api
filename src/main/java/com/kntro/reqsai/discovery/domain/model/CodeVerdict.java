package com.kntro.reqsai.discovery.domain.model;

import org.jspecify.annotations.Nullable;

/**
 * The outcome of verifying a requirement against the pure source code: the capability is already built, the
 * requirement contradicts an implemented rule, nothing in the code relates to it, or the code read is not
 * enough to tell.
 */
public enum CodeVerdict {
    ALREADY_EXISTS,
    CONFLICTS_WITH_CODE,
    NOT_FOUND,
    UNSURE;

    /** The finding a suggestion shows for this verdict; none when the code neither has nor contradicts it. */
    public @Nullable CodeFinding finding() {
        return switch (this) {
            case ALREADY_EXISTS -> CodeFinding.ALREADY_EXISTS;
            case CONFLICTS_WITH_CODE -> CodeFinding.CONFLICTS_WITH_CODE;
            case NOT_FOUND, UNSURE -> null;
        };
    }

    public static @Nullable CodeVerdict parse(@Nullable String value) {
        if (value == null) return null;
        try {
            return valueOf(value.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
