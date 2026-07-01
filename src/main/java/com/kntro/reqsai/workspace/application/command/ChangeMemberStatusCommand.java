package com.kntro.reqsai.workspace.application.command;

import com.kntro.reqsai.workspace.domain.model.MemberStatus;

import java.util.UUID;

/**
 * Changes an organization member's status (ACTIVE &harr; INACTIVE) — deactivate or reactivate.
 * Same RBAC as a role change: OWNER or ADMIN, never the OWNER member, never self-demotion by an admin.
 */
public record ChangeMemberStatusCommand(
        UUID organizationId,
        UUID memberId,
        MemberStatus status,
        UUID requestedBy
) {}
