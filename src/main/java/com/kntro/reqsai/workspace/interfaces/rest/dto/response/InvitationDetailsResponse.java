package com.kntro.reqsai.workspace.interfaces.rest.dto.response;

/**
 * Response of the public {@code GET /api/invitations/{token}}: minimal, non-sensitive info for the
 * accept/signup screen.
 */
public record InvitationDetailsResponse(
        String organizationName,
        String role,
        String email,
        String invitedByName,
        String status,
        boolean expired
) {}
