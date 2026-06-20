package com.kntro.reqsai.iam.application.command;

import java.util.UUID;

/**
 * Changes the password for the authenticated user after verifying the current one.
 *
 * @param userId          the authenticated user's id (from the JWT {@code sub} claim)
 * @param currentPassword the user's current password in plain text (verified before changing)
 * @param newPassword     the desired new password in plain text
 */
public record ChangePasswordCommand(UUID userId, String currentPassword, String newPassword) {}
