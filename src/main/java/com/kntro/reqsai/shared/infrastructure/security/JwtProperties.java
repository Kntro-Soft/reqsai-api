package com.kntro.reqsai.shared.infrastructure.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT <strong>verification</strong> properties, bound from {@code reqsai.jwt.*}.
 * <p>
 * The Shared Kernel only verifies tokens, so it needs just the RSA public key and the expected issuer.
 * The signing key and token lifetimes are <strong>issuance</strong> concerns owned by the {@code iam}
 * bounded context (its {@code TokenIssuer} adapter will bind its own properties under the same prefix).
 *
 * @param publicKeyPath location of the RSA public key (X.509 PEM) — classpath or filesystem path
 * @param issuer        expected {@code iss} claim
 */
@Validated
@ConfigurationProperties(prefix = "reqsai.jwt")
public record JwtProperties(
        @NotBlank String publicKeyPath,
        @NotBlank String issuer
) {
}
