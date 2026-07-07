package com.kntro.reqsai.workspace.application.command;

import java.util.List;
import java.util.UUID;

/**
 * Invites several NEW people (by email) directly to a project in one atomic operation. Each invitation
 * creates a PENDING organization membership (org role MEMBER) carrying the target project and
 * project-role; on accept the member is activated and a {@code ProjectMember} assignment is materialized.
 * Every entry is validated up front; if any is invalid or duplicated the whole request fails and nothing
 * is persisted.
 */
public record InviteProjectMembersCommand(
        UUID organizationId,
        UUID projectId,
        List<Invitation> invitations,
        UUID requestedBy
) {
    public record Invitation(String email, String displayName, UUID roleId) {}
}
