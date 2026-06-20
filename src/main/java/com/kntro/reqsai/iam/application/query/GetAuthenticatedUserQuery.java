package com.kntro.reqsai.iam.application.query;

import java.util.UUID;

/**
 * Intent to load the profile of the authenticated user (the {@code sub} of the current token).
 *
 * @param userId id of the user to load
 */
public record GetAuthenticatedUserQuery(UUID userId) {
}
