package com.kntro.reqsai.workspace.application.command;

import java.util.UUID;

/**
 * Transfers organization ownership to an existing active member. The caller must be the current owner;
 * the target is identified by their member id and its user becomes the new {@code ownerId}.
 */
public record TransferOwnershipCommand(
        UUID organizationId,
        UUID newOwnerMemberId,
        UUID requestedBy
) {}
