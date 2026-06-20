package com.kntro.reqsai.iam.application.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Token policy properties for the IAM bounded context, bound from {@code reqsai.iam.token.*}.
 * <p>
 * Centralises the two TTLs and the token entropy size so they can be tuned per environment
 * without recompiling. Both handlers ({@code ForgotPasswordCommandHandler} and
 * {@code ResendVerificationCommandHandler}) inject this record instead of hard-coding constants.
 *
 * @param tokenBytes number of random bytes in a generated token (default 32 → 64-char hex)
 * @param passwordResetExpiration TTL for password-reset tokens (default 1 h)
 * @param emailVerificationExpiration TTL for email-verification tokens (default 24 h)
 */
@Validated
@ConfigurationProperties(prefix = "reqsai.iam.token")
public record IamTokenProperties(
        @Min(16) int tokenBytes,
        @NotNull Duration passwordResetExpiration,
        @NotNull Duration emailVerificationExpiration
) {
}
