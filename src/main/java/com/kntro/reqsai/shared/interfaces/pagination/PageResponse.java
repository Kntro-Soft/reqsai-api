package com.kntro.reqsai.shared.interfaces.pagination;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Serialization-friendly wrapper around a Spring Data {@link Page}.
 * <p>
 * Exposes a stable JSON contract for paginated REST responses across all bounded contexts.
 *
 * @param content       page items
 * @param page          zero-based page index
 * @param size          page size
 * @param totalElements total matching elements
 * @param totalPages    total number of pages
 * @param first         whether this is the first page
 * @param last          whether this is the last page
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
