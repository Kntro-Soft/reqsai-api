package com.kntro.reqsai.iam.application.result;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Result of a successful token refresh: a new access token and a new raw refresh token.
 * The refresh token is returned as a raw value; the controller stores it in an HttpOnly cookie —
 * it must never appear in the response body.
 *
 * @param accessToken      the new signed JWT
 * @param expiresInSeconds seconds until the new access token expires
 * @param rawRefreshToken  the new 64-char hex refresh token for the HttpOnly cookie
 * @param organizationId   the organization owned by the user, or {@code null} if none exists yet
 */
public record RefreshedSession(String accessToken, long expiresInSeconds, String rawRefreshToken, @Nullable UUID organizationId) {
}
