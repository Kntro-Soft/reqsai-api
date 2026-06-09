package com.kntro.reqsai.shared.interfaces.pagination;

/**
 * Raw pagination/sort parameters as received from a request, before validation.
 * <p>
 * Controllers bind these from query params (e.g. {@code ?page=0&size=20&sortBy=createdAt&sortDirection=DESC})
 * and pass them to {@link PageRequestFactory#toPageable(PageCriteria, SortPolicy)}, which clamps the
 * size and validates the sort against an allowlist. All fields are nullable — defaults are applied
 * downstream.
 *
 * @param page          zero-based page index
 * @param size          requested page size (clamped to the configured maximum)
 * @param sortBy        field to sort by (validated against {@link SortPolicy})
 * @param sortDirection {@code ASC} or {@code DESC}
 */
public record PageCriteria(Integer page, Integer size, String sortBy, String sortDirection) {

    public static PageCriteria of(Integer page, Integer size, String sortBy, String sortDirection) {
        return new PageCriteria(page, size, sortBy, sortDirection);
    }
}
