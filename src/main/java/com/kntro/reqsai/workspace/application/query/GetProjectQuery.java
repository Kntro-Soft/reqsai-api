package com.kntro.reqsai.workspace.application.query;

import java.util.UUID;

/** Query to retrieve a single project by its id, scoped to an organization. */
public record GetProjectQuery(UUID organizationId, UUID projectId) {
}
