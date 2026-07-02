package com.kntro.reqsai.workspace.application.command;

import java.util.UUID;

/**
 * Resends the invitation for a PENDING member: supersedes the current active invitation and issues a
 * fresh one (new token, new expiry, new email). Owner/admin only.
 *
 * @param organizationId the organization the member belongs to
 * @param memberId       the PENDING member to re-invite
 * @param requestedBy    the authenticated caller (must be owner/admin)
 */
public record ResendInvitationCommand(UUID organizationId, UUID memberId, UUID requestedBy) {
}
