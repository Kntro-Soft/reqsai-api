package com.kntro.reqsai.shared.infrastructure.persistence.query;

import org.springframework.data.jpa.domain.Specification;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Functional helpers for composing JPA {@link Specification}s — null/blank-safe, no inheritance.
 * <p>
 * Replaces the per-entity {@code SpecificationComposer} subclass with inline composition in the query
 * handler. Filters whose value is {@code null} (or a blank {@code String}) are skipped automatically;
 * if no filter is active, an empty specification (match-all) is returned.
 *
 * <pre>{@code
 * Specification<User> spec = Specifications.allOf(
 *         Specifications.optional(email,  UserSpecs::emailContains),
 *         Specifications.optional(status, UserSpecs::hasStatus),
 *         Specifications.optional(active, UserSpecs::isActive));
 * Page<User> page = userRepository.findAll(spec, pageable);
 * }</pre>
 */
public final class Specifications {

    private Specifications() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /** Combines the present specifications with AND (match-all when none are present). */
    @SafeVarargs
    public static <T> Specification<T> allOf(Optional<Specification<T>>... specs) {
        return Stream.of(specs)
                .flatMap(Optional::stream)
                .reduce(Specification::and)
                .orElseGet(Specifications::matchAll);
    }

    /** Combines the present specifications with OR (match-all when none are present). */
    @SafeVarargs
    public static <T> Specification<T> anyOf(Optional<Specification<T>>... specs) {
        return Stream.of(specs)
                .flatMap(Optional::stream)
                .reduce(Specification::or)
                .orElseGet(Specifications::matchAll);
    }

    /**
     * Wraps a value into an optional specification: empty when the value is {@code null} or a blank
     * {@code String}, otherwise the spec produced by {@code mapper}.
     */
    public static <T, V> Optional<Specification<T>> optional(V value, Function<V, Specification<T>> mapper) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            return Optional.empty();
        }
        return Optional.of(mapper.apply(value));
    }

    /** A no-op specification that matches every row. */
    public static <T> Specification<T> matchAll() {
        return (root, query, cb) -> cb.conjunction();
    }
}
