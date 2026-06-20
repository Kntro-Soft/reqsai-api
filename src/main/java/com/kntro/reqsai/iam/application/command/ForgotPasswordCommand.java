package com.kntro.reqsai.iam.application.command;

/**
 * Initiates the password-reset flow by sending a one-time reset link to the given email address.
 * Always completes successfully (204) regardless of whether the email is registered, to prevent
 * account enumeration.
 *
 * @param email the email address that requested the password reset
 */
public record ForgotPasswordCommand(String email) {}
