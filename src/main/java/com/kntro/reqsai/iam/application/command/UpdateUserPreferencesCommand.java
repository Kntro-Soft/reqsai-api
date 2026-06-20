package com.kntro.reqsai.iam.application.command;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Updates the navigation preferences of an authenticated user.
 * {@code lastVisitedOrgId} is nullable: sending {@code null} clears the preference and the next
 * login/refresh falls back to the most-recently created organization.
 */
public record UpdateUserPreferencesCommand(
        UUID userId,
        @Nullable UUID lastVisitedOrgId
) {
}
