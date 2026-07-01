package com.kntro.reqsai.workspace.application.command;

import java.util.UUID;

/** Deletes an organization (owner-only) and deprovisions its tenant schema. */
public record DeleteOrganizationCommand(
        UUID organizationId,
        UUID requestedBy
) {}
