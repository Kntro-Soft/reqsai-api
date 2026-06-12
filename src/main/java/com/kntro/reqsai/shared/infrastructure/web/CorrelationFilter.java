package com.kntro.reqsai.shared.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Adds a correlation id to every request for end-to-end traceability.
 * <p>
 * Registered as the first filter in the Spring Security chain (not as a standalone servlet filter),
 * so the id is present for every request the chain handles. It reuses an incoming {@code X-Request-ID},
 * or generates one, stores it in the MDC (so it appears in every log line and in error responses), and
 * echoes it back in the {@code X-Request-ID} response header. The {@code tenantId} is seeded as
 * {@code system} and later overwritten by {@code JwtAuthenticationFilter} once the tenant is resolved.
 * The MDC is always cleared in a {@code finally} block to avoid leakage across pooled threads.
 */
public class CorrelationFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID = "correlationId";
    public static final String TENANT_ID = "tenantId";
    private static final String HEADER = "X-Request-ID";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String correlationId = resolveId(request.getHeader(HEADER));
        MDC.put(CORRELATION_ID, correlationId);
        MDC.put(TENANT_ID, "system");
        try {
            response.setHeader(HEADER, correlationId);
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private static String resolveId(String header) {
        return StringUtils.hasText(header) ? header : UUID.randomUUID().toString();
    }
}
