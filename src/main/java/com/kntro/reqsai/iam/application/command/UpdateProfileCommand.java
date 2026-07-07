package com.kntro.reqsai.iam.application.command;

import java.util.UUID;

/**
 * Updates the editable profile fields for the authenticated user.
 *
 * @param userId    the authenticated user's id (from the JWT {@code sub} claim)
 * @param firstName new first name
 * @param lastName  new last name
 */
public record UpdateProfileCommand(UUID userId, String firstName, String lastName) {}
