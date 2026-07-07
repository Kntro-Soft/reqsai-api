package com.kntro.reqsai.discovery.application.query;

import java.util.UUID;

/** Query to retrieve a single discovery session by its id, scoped to a project. */
public record GetProjectSessionQuery(UUID projectId, UUID sessionId) {
}
