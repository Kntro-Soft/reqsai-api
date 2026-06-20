package com.kntro.reqsai.iam.infrastructure.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * JWT <strong>issuance</strong> properties owned by the IAM bounded context, bound from {@code reqsai.jwt.*}
 * (the same prefix the Shared Kernel's verification {@code JwtProperties} reads — each binds its own
 * subset). Issuance needs the RSA <strong>private</strong> signing key, the {@code iss} claim and the
 * access-token lifetime.
 *
 * @param privateKeyPath           location of the RSA private key (PKCS#8 PEM) — classpath or filesystem
 * @param issuer                   value placed in the {@code iss} claim (must match what the verifier requires)
 * @param accessTokenExpiration    access-token lifetime (e.g. {@code 15m})
 * @param refreshTokenExpiration   refresh-token lifetime (e.g. {@code 7d})
 */
@Validated
@ConfigurationProperties(prefix = "reqsai.jwt")
public record IamJwtProperties(
        @NotBlank String privateKeyPath,
        @NotBlank String issuer,
        @NotNull Duration accessTokenExpiration,
        @NotNull Duration refreshTokenExpiration
) {
}
