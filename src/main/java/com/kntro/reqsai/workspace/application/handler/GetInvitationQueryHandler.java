package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.support.HashUtils;
import com.kntro.reqsai.workspace.application.port.InvitationRepository;
import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.query.GetInvitationQuery;
import com.kntro.reqsai.workspace.application.result.InvitationDetails;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Invitation;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import com.kntro.reqsai.workspace.domain.model.Organization;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Public (no-auth) lookup of an invitation by raw token, for the accept/signup screen. Returns only
 * non-sensitive fields — org name, role, invited email, inviter name, status and whether it expired —
 * never the token hash or member/organization ids. 404 when the token is unknown.
 */
@Component
@RequiredArgsConstructor
public class GetInvitationQueryHandler {

    private final InvitationRepository invitations;
    private final OrganizationRepository organizations;
    private final MemberRepository members;

    @Transactional(readOnly = true)
    public InvitationDetails handle(GetInvitationQuery query) {
        Invitation invitation = invitations.findByTokenHash(HashUtils.sha256(query.rawToken()))
                .orElseThrow(WorkspaceExceptions::invitationNotFound);

        Organization organization = organizations.findById(invitation.getOrganizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(invitation.getOrganizationId()));

        boolean expired = invitation.isPending() && !invitation.isValid(Instant.now());
        return new InvitationDetails(
                organization.getName(),
                invitation.getRole().name(),
                invitation.getEmail(),
                resolveInviterName(invitation.getOrganizationId(), invitation.getInvitedBy()),
                invitation.getStatus().name(),
                expired);
    }

    private @Nullable String resolveInviterName(UUID organizationId, UUID invitedBy) {
        return members.findByOrganizationIdAndUserIdAndStatus(organizationId, invitedBy, MemberStatus.ACTIVE)
                .map(m -> m.getDisplayName())
                .orElse(null);
    }
}
