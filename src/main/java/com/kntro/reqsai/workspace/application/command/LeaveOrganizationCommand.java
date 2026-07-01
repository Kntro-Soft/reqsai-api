package com.kntro.reqsai.workspace.application.command;

import java.util.UUID;

/** Removes the caller's own active membership from an organization. The owner cannot leave. */
public record LeaveOrganizationCommand(
        UUID organizationId,
        UUID requestedBy
) {}
