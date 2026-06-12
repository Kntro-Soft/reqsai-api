package com.kntro.reqsai.shared.domain.support;

import com.kntro.reqsai.shared.domain.exception.CommonError;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.shared.domain.exception.ErrorCatalog;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.regex.Pattern;

/**
 * Domain assertion helpers for guarding aggregate / value-object invariants.
 * <p>
 * Methods return the validated value for fluent assignment and throw a {@link DomainException} on
 * failure. Each check has a default variant (using {@link CommonError#INVALID_FIELD}) and an overload
 * that takes a specific {@link ErrorCatalog} — so a bounded context can pass its own code. String
 * checks trim whitespace.
 *
 * <pre>{@code
 * this.name  = Assert.notBlank(name, "name");
 * this.name  = Assert.maxLength(name, "name", 100);
 * this.slug  = Assert.matches(slug, "slug", SLUG_PATTERN);
 * this.price = Assert.positiveOrZero(price, "price");
 * Assert.notEmpty(items, "items");
 * }</pre>
 */
public final class Assert {

    private Assert() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    // notBlank
    public static String notBlank(String value, String field) {
        return notBlank(value, field, CommonError.INVALID_FIELD);
    }

    public static String notBlank(String value, String field, ErrorCatalog code) {
        if (value == null || value.isBlank()) {
            throw new DomainException(code, "'%s' cannot be null or blank".formatted(field));
        }
        return value.trim();
    }

    // notNull
    public static <X> X notNull(X value, String field) {
        return notNull(value, field, CommonError.INVALID_FIELD);
    }

    public static <X> X notNull(X value, String field, ErrorCatalog code) {
        if (value == null) {
            throw new DomainException(code, "'%s' cannot be null".formatted(field));
        }
        return value;
    }

    // isTrue
    public static void isTrue(boolean condition, String field, String message) {
        isTrue(condition, field, message, CommonError.INVALID_FIELD);
    }

    public static void isTrue(boolean condition, String field, String message, ErrorCatalog code) {
        if (!condition) {
            throw new DomainException(code, "'%s': %s".formatted(field, message));
        }
    }

    // maxLength
    public static String maxLength(String value, String field, int max) {
        return maxLength(value, field, max, CommonError.INVALID_FIELD);
    }

    public static String maxLength(String value, String field, int max, ErrorCatalog code) {
        if (value != null && value.length() > max) {
            throw new DomainException(code,
                    "'%s' must be at most %d characters".formatted(field, max));
        }
        return value;
    }

    // matches
    public static String matches(String value, String field, Pattern pattern) {
        return matches(value, field, pattern, CommonError.INVALID_FIELD);
    }

    public static String matches(String value, String field, Pattern pattern, ErrorCatalog code) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new DomainException(code, "'%s' has an invalid format".formatted(field));
        }
        return value;
    }

    // positiveOrZero / positive
    public static BigDecimal positiveOrZero(BigDecimal value, String field) {
        return positiveOrZero(value, field, CommonError.INVALID_FIELD);
    }

    public static BigDecimal positiveOrZero(BigDecimal value, String field, ErrorCatalog code) {
        if (value == null || value.signum() < 0) {
            throw new DomainException(code, "'%s' must be zero or positive".formatted(field));
        }
        return value;
    }

    public static BigDecimal positive(BigDecimal value, String field) {
        return positive(value, field, CommonError.INVALID_FIELD);
    }

    public static BigDecimal positive(BigDecimal value, String field, ErrorCatalog code) {
        if (value == null || value.signum() <= 0) {
            throw new DomainException(code, "'%s' must be positive".formatted(field));
        }
        return value;
    }

    // notEmpty (collections)
    public static <C extends Collection<?>> C notEmpty(C value, String field) {
        return notEmpty(value, field, CommonError.INVALID_FIELD);
    }

    public static <C extends Collection<?>> C notEmpty(C value, String field, ErrorCatalog code) {
        if (value == null || value.isEmpty()) {
            throw new DomainException(code, "'%s' must not be empty".formatted(field));
        }
        return value;
    }
}
