package com.kntro.reqsai.testsupport;

import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.util.List;

/**
 * Builds the {@code SecurityContext} for {@link WithMockReqsaiUser}, mirroring the authentication that
 * {@code JwtAuthenticationFilter} sets on a real request (principal = userId, one authority = role).
 */
class WithMockReqsaiUserSecurityContextFactory implements WithSecurityContextFactory<WithMockReqsaiUser> {

    @Override
    public @NonNull SecurityContext createSecurityContext(WithMockReqsaiUser annotation) {
        var authentication = new UsernamePasswordAuthenticationToken(
                annotation.userId(),
                null,
                List.of(new SimpleGrantedAuthority(annotation.role())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        return context;
    }
}
