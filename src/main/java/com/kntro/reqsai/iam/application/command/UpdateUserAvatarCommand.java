package com.kntro.reqsai.iam.application.command;

import java.util.UUID;

/**
 * Replaces the authenticated user's avatar with an uploaded image.
 *
 * @param userId      the authenticated user's id (from the JWT {@code sub} claim)
 * @param bytes       the validated image bytes
 * @param contentType the image content type
 */
public record UpdateUserAvatarCommand(UUID userId, byte[] bytes, String contentType) {}
