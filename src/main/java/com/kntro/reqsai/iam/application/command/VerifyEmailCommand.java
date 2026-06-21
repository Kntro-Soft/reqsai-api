package com.kntro.reqsai.iam.application.command;

/**
 * Triggers email address verification using the one-time token delivered to the user's inbox.
 *
 * @param rawToken the raw (unhashed) token extracted from the verification link
 */
public record VerifyEmailCommand(String rawToken) {}
