package com.kntro.reqsai.shared.infrastructure.security;

import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantSchemaResolver;
import com.kntro.reqsai.shared.infrastructure.web.CorrelationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates requests from a Bearer JWT and binds the tenant for the request's lifetime.
 * <p>
 * Depends on the {@link TokenVerifier} port (not a concrete JWT library). On a valid token it resolves
 * the {@code orgId} claim to a schema via {@link TenantSchemaResolver} and stores both in
 * {@link TenantContext} (+ the {@code tenantId} in the MDC); then populates the
 * {@link SecurityContextHolder} with the user id and role. {@link TenantContext} is always cleared in a
 * {@code finally} block to prevent leakage across pooled threads.
 * <p>
 * Requests without a token pass through unauthenticated and are rejected by the authorization rules in
 * {@link SecurityConfiguration} (except the declared public endpoints).
 */
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenVerifier tokenVerifier;
    private final TenantSchemaResolver tenantSchemaResolver;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractToken(request);
            if (StringUtils.hasText(token)) {
                VerifiedToken verified = tokenVerifier.verify(token);

                if (StringUtils.hasText(verified.orgId())) {
                    TenantContext.setCurrentTenant(verified.orgId());
                    TenantContext.setCurrentSchema(tenantSchemaResolver.resolveTenantSchema(verified.orgId()));
                    MDC.put(CorrelationFilter.TENANT_ID, verified.orgId());
                }

                var authentication = new UsernamePasswordAuthenticationToken(
                        verified.userId(), null,
                        verified.role() != null ? List.of(new SimpleGrantedAuthority(verified.role())) : List.of());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Authenticated user {} (tenant {})", verified.userId(), verified.orgId());
            }
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            log.debug("JWT authentication skipped: {}", e.getMessage());
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
