package com.kntro.reqsai.iam.application.result;

import com.kntro.reqsai.iam.domain.model.User;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Result of a successful authentication: the issued access token (and its lifetime) plus the
 * authenticated user's profile and the raw refresh token for the HttpOnly cookie.
 *
 * @param accessToken      the signed JWT to send as {@code Authorization: Bearer <token>}
 * @param expiresInSeconds seconds until the access token expires
 * @param rawRefreshToken  the 64-char hex refresh token — the controller sets this as an HttpOnly cookie;
 *                         it is NEVER serialised to the response body
 * @param user             the authenticated user
 * @param email            the authenticated user's account email
 * @param organizationId   the organization owned by the user, or {@code null} if none exists yet
 */
public record AuthenticatedSession(String accessToken, long expiresInSeconds, String rawRefreshToken, User user, String email, @Nullable UUID organizationId) {
}
