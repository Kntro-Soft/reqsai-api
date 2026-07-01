package com.kntro.reqsai.workspace.application.result;

import java.util.UUID;

/**
 * Outcome of accepting an invitation — enough for the frontend to route the user into the joined
 * organization.
 *
 * @param organizationId   the organization the caller joined
 * @param organizationName its display name
 * @param memberId         the now-ACTIVE member row for the caller
 * @param role             the org role granted
 */
public record AcceptInvitationResult(UUID organizationId, String organizationName, UUID memberId, String role) {
}
