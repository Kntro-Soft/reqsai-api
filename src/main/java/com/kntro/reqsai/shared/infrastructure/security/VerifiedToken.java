package com.kntro.reqsai.shared.infrastructure.security;

/**
 * The trusted claims extracted from a verified access token.
 *
 * @param userId subject — the authenticated user id
 * @param orgId  tenant id (organization); {@code null} for platform-level tokens
 * @param role   granted authority
 */
public record VerifiedToken(String userId, String orgId, String role) {
}
