package com.kntro.reqsai.workspace.application.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Invitation policy properties for the Workspace bounded context, bound from
 * {@code reqsai.invitation.*}. Mirrors {@code IamTokenProperties}: keeps the acceptance-token TTL
 * configurable per environment instead of hard-coding a constant.
 *
 * @param expiry how long an issued invitation stays valid before it must be resent (default 7d)
 */
@Validated
@ConfigurationProperties(prefix = "reqsai.invitation")
public record InvitationProperties(
        @NotNull Duration expiry
) {
}
