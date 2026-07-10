package com.kntro.reqsai.workspace.application.query;

import java.util.UUID;

/** Resolves the caller's effective permissions on a project (of the currently bound tenant). */
public record GetMyProjectPermissionsQuery(
        UUID projectId,
        UUID requestedBy
) {}
