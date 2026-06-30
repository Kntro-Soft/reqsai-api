package com.kntro.reqsai.iam.application.query;

import java.util.UUID;

/** Query to retrieve a user's stored avatar bytes by user id. */
public record GetUserAvatarQuery(UUID userId) {
}
