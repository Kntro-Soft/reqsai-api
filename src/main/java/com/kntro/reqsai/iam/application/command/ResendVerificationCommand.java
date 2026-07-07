package com.kntro.reqsai.iam.application.command;

/**
 * Resends the email-verification link for an account that is still {@code PENDING_VERIFICATION}.
 * Always completes successfully (204) regardless of whether the email is registered, to prevent
 * account enumeration.
 *
 * @param email the email address whose verification should be resent
 */
public record ResendVerificationCommand(String email) {}
