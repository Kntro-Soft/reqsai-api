package com.kntro.reqsai.shared.domain.valueobjects;

import com.kntro.reqsai.shared.domain.exception.Exceptions;
import org.jspecify.annotations.NonNull;

import java.util.regex.Pattern;

/**
 * Email value object — self-validating, immutable, normalized to lowercase.
 * <p>
 * Reference value object demonstrating the Shared Kernel pattern (record + compact-constructor
 * validation). Bounded contexts can reuse it for any email field.
 */
public record Email(String value) {

    private static final Pattern PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public Email {
        if (value == null || value.isBlank()) {
            throw Exceptions.invalidValue("email", "cannot be null or blank");
        }
        value = value.trim().toLowerCase();
        if (!PATTERN.matcher(value).matches()) {
            throw Exceptions.invalidValue("email", "'%s' is not a valid email address".formatted(value));
        }
    }

    public static Email of(String value) {
        return new Email(value);
    }

    @Override
    public @NonNull String toString() {
        return value;
    }
}
