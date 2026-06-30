package com.kntro.reqsai.workspace.application.query;

import java.util.UUID;

/** Query to retrieve a project's stored avatar bytes, scoped to an organization. */
public record GetProjectAvatarQuery(UUID organizationId, UUID projectId) {
}
