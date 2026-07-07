package com.kntro.reqsai.workspace.application.command;

import com.kntro.reqsai.workspace.domain.model.OrgRole;

import java.util.List;
import java.util.UUID;

/**
 * Invites several members in one atomic operation. Every invitation is validated up front; if any is
 * invalid or duplicated the whole request fails and nothing is persisted.
 */
public record BatchInviteMembersCommand(
        UUID organizationId,
        List<Invitation> invitations,
        UUID requestedBy
) {
    public record Invitation(String email, String displayName, OrgRole role) {}
}
