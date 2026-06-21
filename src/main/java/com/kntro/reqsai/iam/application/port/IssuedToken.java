package com.kntro.reqsai.iam.application.port;

/**
 * A freshly issued access token together with its lifetime.
 *
 * @param token            the signed compact JWT
 * @param expiresInSeconds seconds until the token expires
 */
public record IssuedToken(String token, long expiresInSeconds) {
}
