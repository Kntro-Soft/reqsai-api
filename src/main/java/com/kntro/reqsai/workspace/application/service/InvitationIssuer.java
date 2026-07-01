package com.kntro.reqsai.workspace.application.service;

import com.kntro.reqsai.shared.domain.support.TokenGenerator;
import com.kntro.reqsai.workspace.application.config.InvitationProperties;
import com.kntro.reqsai.workspace.application.port.InvitationRepository;
import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.domain.model.Invitation;
import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import com.kntro.reqsai.workspace.domain.model.Organization;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Issues tokenized {@link Invitation}s for PENDING members and persists them. Shared by the single
 * and batch invite handlers and by resend, so token generation, TTL and the inviter-name resolution
 * live in one place. Persisting the aggregate publishes its {@code MemberInvitedEvent} (the raw token
 * travels only in that event), which the workspace email listener turns into an acceptance link.
 */
@Component
@RequiredArgsConstructor
public class InvitationIssuer {

    private static final int TOKEN_BYTES = 32;

    private final InvitationRepository invitations;
    private final MemberRepository members;
    private final InvitationProperties properties;

    /**
     * Issues a fresh PENDING invitation for {@code member} (assumed PENDING). Any existing PENDING
     * invitation for the member is superseded first, so only one stays active.
     */
    public Invitation issueFor(Organization organization, Member member, UUID invitedBy) {
        invitations.findByMemberIdAndStatus(member.getId(), com.kntro.reqsai.workspace.domain.model.InvitationStatus.PENDING)
                .ifPresent(existing -> {
                    existing.supersede();
                    invitations.save(existing);
                });

        String rawToken = TokenGenerator.generate(TOKEN_BYTES);
        Invitation invitation = Invitation.issue(
                organization.getId(),
                organization.getName(),
                member.getId(),
                member.getEmail(),
                member.getDisplayName(),
                member.getRole(),
                rawToken,
                invitedBy,
                resolveInviterName(organization.getId(), invitedBy),
                Instant.now().plus(properties.expiry()));
        return invitations.save(invitation);
    }

    /** Best-effort inviter display name from their ACTIVE member row; {@code null} when not a member. */
    private @Nullable String resolveInviterName(UUID organizationId, UUID invitedBy) {
        return members.findByOrganizationIdAndUserIdAndStatus(organizationId, invitedBy, MemberStatus.ACTIVE)
                .map(Member::getDisplayName)
                .orElse(null);
    }
}
