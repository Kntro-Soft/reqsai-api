package com.kntro.reqsai.shared.interfaces.pagination;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Pagination limits, bound from {@code reqsai.pagination.*}.
 * <p>
 * Externalized (not hardcoded at call sites) so page sizes can be tuned per environment and, later,
 * per plan. {@link PageRequestFactory} consumes these to clamp a requested size. The fallback values
 * live in the {@link #DEFAULT_PAGE_SIZE}/{@link #MAX_PAGE_SIZE} constants — not magic numbers.
 *
 * @param defaultSize page size when the client does not specify one
 * @param maxSize     hard upper bound on page size
 */
@ConfigurationProperties(prefix = "reqsai.pagination")
public record PaginationProperties(int defaultSize, int maxSize) {

    /** Default page size when none is requested. */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** Hard upper bound on page size, regardless of what the client asks for. */
    public static final int MAX_PAGE_SIZE = 100;

    public PaginationProperties {
        if (defaultSize <= 0) {
            defaultSize = DEFAULT_PAGE_SIZE;
        }
        if (maxSize <= 0) {
            maxSize = MAX_PAGE_SIZE;
        }
    }

    /**
     * Clamps a requested page size into {@code [1, maxSize]}, falling back to {@link #defaultSize()}
     * when the request omits it ({@code null} or {@code <= 0}).
     */
    public int resolveSize(Integer requested) {
        if (requested == null || requested <= 0) {
            return defaultSize;
        }
        return Math.min(requested, maxSize);
    }
}
