package com.kntro.reqsai.shared.interfaces.pagination;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * Builds {@link Pageable}s from request input, applying the configured {@link PaginationProperties}
 * limits and a {@link SortPolicy} (validated sort + {@code id} tie-breaker).
 * <p>
 * Controllers/query handlers use this instead of trusting a client-supplied {@code size} or sort
 * field directly. Page index is zero-based and floored at 0.
 */
@Component
public class PageRequestFactory {

    private final PaginationProperties properties;

    public PageRequestFactory(PaginationProperties properties) {
        this.properties = properties;
    }

    /** Builds a {@link Pageable} from raw criteria, validating the sort against the given policy. */
    public Pageable toPageable(PageCriteria criteria, SortPolicy sortPolicy) {
        return PageRequest.of(
                resolvePage(criteria.page()),
                properties.resolveSize(criteria.size()),
                sortPolicy.toSort(criteria.sortBy(), criteria.sortDirection()));
    }

    /** Builds a {@link Pageable} with an explicit, already-trusted sort. */
    public Pageable toPageable(Integer page, Integer size, Sort sort) {
        return PageRequest.of(resolvePage(page), properties.resolveSize(size), sort);
    }

    /** Builds an unsorted {@link Pageable}. */
    public Pageable toPageable(Integer page, Integer size) {
        return toPageable(page, size, Sort.unsorted());
    }

    private int resolvePage(Integer page) {
        return (page == null || page < 0) ? 0 : page;
    }
}
