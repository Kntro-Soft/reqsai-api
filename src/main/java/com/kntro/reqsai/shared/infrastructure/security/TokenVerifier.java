package com.kntro.reqsai.shared.infrastructure.security;

/**
 * Port for <strong>verifying</strong> access tokens on every request.
 * <p>
 * Verification is cross-cutting (the security filter chain in {@code shared} runs it for all modules),
 * so the port lives here. Token <strong>issuance</strong> (login/refresh) is a different concern owned
 * by the {@code iam} bounded context — it will define its own {@code TokenIssuer} port and signing
 * adapter. With RS256 this split is natural: verification needs only the public key, signing needs the
 * private key.
 * <p>
 * The default adapter is {@link JwtTokenVerifier} (JJWT); swapping the JWT library means swapping the
 * adapter, not touching {@link JwtAuthenticationFilter}.
 */
public interface TokenVerifier {

    /**
     * Verifies the token's signature, issuer and expiry and returns its trusted claims.
     *
     * @param token the raw JWT (no {@code Bearer } prefix)
     * @return the verified claims
     * @throws com.kntro.reqsai.shared.domain.exception.AuthenticationException if invalid or expired
     */
    VerifiedToken verify(String token);
}
