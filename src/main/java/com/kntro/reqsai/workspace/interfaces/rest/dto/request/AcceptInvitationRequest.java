package com.kntro.reqsai.workspace.interfaces.rest.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /api/invitations/accept}: the raw invitation token from the acceptance link.
 */
public record AcceptInvitationRequest(
        @NotBlank String token
) {}
