package com.kntro.reqsai.testsupport;

import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Populates the {@code SecurityContext} exactly like {@code JwtAuthenticationFilter} does on a valid
 * token — principal = {@code userId}, a single authority = {@code role} — but without minting or parsing
 * a JWT.
 * <p>
 * Use on {@code @WebMvcTest} slice tests and method-security tests, where you only need an authenticated
 * principal in the context. For end-to-end coverage that actually crosses the JWT filter, send a real
 * token from {@link TestJwtFactory} instead.
 *
 * <pre>{@code
 * @Test
 * @WithMockReqsaiUser(role = "ROLE_ADMIN")
 * void adminCanListWorkspaces() throws Exception {
 *     mockMvc.perform(get("/api/v1/workspaces")).andExpect(status().isOk());
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@WithSecurityContext(factory = WithMockReqsaiUserSecurityContextFactory.class)
public @interface WithMockReqsaiUser {

    /** Becomes the authentication principal (the JWT {@code sub}). */
    String userId() default "00000000-0000-0000-0000-000000000001";

    /** The tenant id (JWT {@code orgId} claim) — set for tests that read it from the context. */
    String orgId() default "00000000-0000-0000-0000-000000000009";

    /** Single granted authority, stored verbatim (e.g. {@code ROLE_ADMIN}). */
    String role() default "ROLE_USER";
}
