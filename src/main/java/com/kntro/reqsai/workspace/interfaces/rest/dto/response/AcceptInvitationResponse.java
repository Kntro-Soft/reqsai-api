package com.kntro.reqsai.workspace.interfaces.rest.dto.response;

import java.util.UUID;

/**
 * Response of {@code POST /api/invitations/accept}: where the caller just landed, so the frontend can
 * route them into the organization.
 */
public record AcceptInvitationResponse(
        UUID organizationId,
        String organizationName,
        UUID memberId,
        String role
) {}
