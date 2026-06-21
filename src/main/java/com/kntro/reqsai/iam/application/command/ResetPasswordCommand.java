package com.kntro.reqsai.iam.application.command;

/**
 * Applies a password reset using the one-time token delivered by the forgot-password flow.
 *
 * @param rawToken    the raw (unhashed) token from the reset link
 * @param newPassword the desired new password in plain text
 */
public record ResetPasswordCommand(String rawToken, String newPassword) {}
