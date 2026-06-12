package com.kntro.reqsai.shared.interfaces.pagination;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable JSON envelope for paginated REST responses.
 * <p>
 * Uses a nested {@code page} metadata object ({@code {content, page:{...}}}) — the same shape as
 * Spring's {@code PagedModel} — so the contract stays stable regardless of internal changes.
 *
 * @param <T> item type
 */
public record PageResponse<T>(List<T> content, PageInfo page) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                new PageInfo(
                        page.getNumber(),
                        page.getSize(),
                        page.getTotalElements(),
                        page.getTotalPages(),
                        page.isFirst(),
                        page.isLast(),
                        page.hasNext(),
                        page.hasPrevious()));
    }

    /**
     * Pagination metadata.
     *
     * @param number        zero-based page index
     * @param size          page size
     * @param totalElements total matching elements across all pages
     * @param totalPages    total number of pages
     * @param first         whether this is the first page
     * @param last          whether this is the last page
     * @param hasNext       whether a next page exists
     * @param hasPrevious   whether a previous page exists
     */
    public record PageInfo(
            int number,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last,
            boolean hasNext,
            boolean hasPrevious
    ) {
    }
}
