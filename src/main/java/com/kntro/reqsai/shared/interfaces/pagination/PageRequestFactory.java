package com.kntro.reqsai.shared.interfaces.pagination;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * Builds {@link Pageable}s from raw request parameters, applying the configured
 * {@link PaginationProperties} limits.
 * <p>
 * Controllers/query handlers use this instead of trusting client-supplied {@code size} directly, so
 * a request can never demand an unbounded page. Page index is zero-based and floored at 0.
 */
@Component
public class PageRequestFactory {

    private final PaginationProperties properties;

    public PageRequestFactory(PaginationProperties properties) {
        this.properties = properties;
    }

    public Pageable of(Integer page, Integer size) {
        return PageRequest.of(resolvePage(page), properties.resolveSize(size));
    }

    public Pageable of(Integer page, Integer size, Sort sort) {
        return PageRequest.of(resolvePage(page), properties.resolveSize(size), sort);
    }

    private int resolvePage(Integer page) {
        return (page == null || page < 0) ? 0 : page;
    }
}
