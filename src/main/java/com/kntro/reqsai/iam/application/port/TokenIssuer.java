package com.kntro.reqsai.iam.application.port;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Token issuance port — the real counterpart of the Shared Kernel's {@code TokenVerifier}. Mints signed
 * access tokens carrying {@code sub}=userId, {@code orgId} (optional) and {@code role}. Implemented in
 * {@code infrastructure} (RS256 JWT).
 */
public interface TokenIssuer {

    /**
     * Issues a signed access token.
     *
     * @param userId       the authenticated user id (becomes the {@code sub} claim)
     * @param orgId        the active organization/tenant id ({@code orgId} claim), or {@code null}
     *                     when the user has no active organization yet
     * @param role         the granted role (e.g. {@code ROLE_USER})
     * @param termsVersion the T&C version the user last accepted (e.g. {@code "2026-01"}), or
     *                     {@code null} if the user has not yet accepted the current terms — the
     *                     frontend reads this claim to gate the onboarding flow
     * @return the signed token and its lifetime in seconds
     */
    IssuedToken issue(UUID userId, @Nullable UUID orgId, String role, @Nullable String termsVersion);
}
