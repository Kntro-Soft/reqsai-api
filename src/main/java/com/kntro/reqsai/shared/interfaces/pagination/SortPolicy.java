package com.kntro.reqsai.shared.interfaces.pagination;

import org.springframework.data.domain.Sort;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Per-query sorting policy: which fields may be sorted on, and the default order.
 * <p>
 * Replaces the inheritance-based {@code SortConfiguration}/{@code SortMapper} duo with a plain,
 * composable value. Define one constant per query using JPA metamodel constants for type safety:
 *
 * <pre>{@code
 * static final SortPolicy SORT = SortPolicy.of(
 *         User_.CREATED_AT, Sort.Direction.DESC,
 *         User_.EMAIL, User_.CREATED_AT);
 * Sort sort = SORT.toSort(criteria.sortBy(), criteria.sortDirection());
 * }</pre>
 *
 * <strong>Why a whitelist:</strong> the requested {@code sortBy} is validated against
 * {@link #allowedFields()} — an unknown field falls back to the default, preventing
 * property-injection. <strong>Why a tie-breaker:</strong> a stable, deterministic order across pages
 * requires a unique trailing key, so {@code id} is appended automatically unless it is already the
 * sort field (offset pagination on a non-unique sort can otherwise drop/duplicate rows between pages).
 *
 * @param allowedFields    sortable field names (entity property paths)
 * @param defaultField     field used when the request omits/invalid {@code sortBy}
 * @param defaultDirection direction used when the request omits/invalid {@code sortDirection}
 */
public record SortPolicy(Set<String> allowedFields, String defaultField, Sort.Direction defaultDirection) {

    private static final String TIE_BREAKER = "id";

    public SortPolicy {
        allowedFields = Set.copyOf(allowedFields);
    }

    /**
     * Convenience factory: {@code SortPolicy.of(defaultField, defaultDirection, allowed...)}.
     * The default field is always included in the allowlist.
     */
    public static SortPolicy of(String defaultField, Sort.Direction defaultDirection, String... allowedFields) {
        Set<String> allowed = new LinkedHashSet<>(Set.of(allowedFields));
        allowed.add(defaultField);
        return new SortPolicy(allowed, defaultField, defaultDirection);
    }

    /**
     * Builds a validated {@link Sort}: the field is checked against the allowlist (falling back to
     * the default), the direction is parsed leniently, and {@code id} is appended as a tie-breaker.
     */
    public Sort toSort(String sortBy, String sortDirection) {
        String field = (sortBy != null && allowedFields.contains(sortBy)) ? sortBy : defaultField;
        Sort.Direction direction = parseDirection(sortDirection);
        Sort sort = Sort.by(direction, field);
        return TIE_BREAKER.equals(field) ? sort : sort.and(Sort.by(Sort.Direction.ASC, TIE_BREAKER));
    }

    /** The default sort (no request input), with the {@code id} tie-breaker applied. */
    public Sort defaultSort() {
        return toSort(null, null);
    }

    private Sort.Direction parseDirection(String sortDirection) {
        if (sortDirection == null) {
            return defaultDirection;
        }
        try {
            return Sort.Direction.fromString(sortDirection);
        } catch (IllegalArgumentException ignored) {
            return defaultDirection;
        }
    }
}
