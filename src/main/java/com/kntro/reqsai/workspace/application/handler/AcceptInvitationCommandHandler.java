package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.iam.application.port.AccountLookupPort;
import com.kntro.reqsai.shared.domain.support.HashUtils;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantSchemaResolver;
import com.kntro.reqsai.workspace.application.command.AcceptInvitationCommand;
import com.kntro.reqsai.workspace.application.port.InvitationRepository;
import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.result.AcceptInvitationResult;
import com.kntro.reqsai.workspace.application.service.ProjectAssignmentMaterializer;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Invitation;
import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import com.kntro.reqsai.workspace.domain.model.Organization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Accepts an organization invitation. Two trust anchors are required: possession of the raw token AND
 * ownership of the invited email (the caller's account email must match the invitation email,
 * case-insensitively). The accept is token-scoped but email-bound — see ADR-0021.
 * <p>
 * Resolution: hash the token → find the invitation (404 if unknown). If already ACCEPTED, return
 * idempotently. If PENDING but past expiry, mark EXPIRED and signal 410. Otherwise enforce the
 * email match (403 on mismatch), then link the PENDING member to the caller ({@link Member#reactivate}
 * → ACTIVE) and mark the invitation ACCEPTED.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AcceptInvitationCommandHandler {

    private final InvitationRepository invitations;
    private final MemberRepository members;
    private final OrganizationRepository organizations;
    private final AccountLookupPort accountLookup;
    private final ProjectAssignmentMaterializer projectAssignmentMaterializer;
    private final TenantSchemaResolver tenantSchemaResolver;

    @Transactional
    public AcceptInvitationResult handle(AcceptInvitationCommand command) {
        Invitation invitation = invitations.findByTokenHash(HashUtils.sha256(command.rawToken()))
                .orElseThrow(WorkspaceExceptions::invitationNotFound);

        Organization organization = organizations.findById(invitation.getOrganizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(invitation.getOrganizationId()));

        // Idempotent: replaying accept on an already-accepted invitation returns 200 with the same info.
        if (invitation.isAccepted()) {
            return toResult(organization, invitation);
        }

        Instant now = Instant.now();
        if (!invitation.isValid(now)) {
            invitation.markExpired(now);
            invitations.save(invitation);
            throw WorkspaceExceptions.invitationExpired();
        }

        // Email-match policy: the caller must own the invited email (token possession is not enough).
        String callerEmail = accountLookup.findEmailByUserId(command.callerId())
                .orElseThrow(WorkspaceExceptions::invitationEmailMismatch);
        if (!callerEmail.equalsIgnoreCase(invitation.getEmail())) {
            throw WorkspaceExceptions.invitationEmailMismatch();
        }

        Member member = members.findByIdAndOrganizationIdAndStatusIn(
                        invitation.getMemberId(), invitation.getOrganizationId(),
                        List.of(MemberStatus.PENDING, MemberStatus.ACTIVE))
                .orElseThrow(WorkspaceExceptions::invitationNotFound);

        if (member.getStatus() == MemberStatus.PENDING) {
            member.reactivate(command.callerId());
            members.save(member);
        }
        invitation.markAccepted(now);
        invitations.save(invitation);

        materializeProjectAssignment(invitation, member);

        log.info("Invitation {} accepted by user {} (org {})",
                invitation.getId(), command.callerId(), invitation.getOrganizationId());
        return toResult(organization, invitation);
    }

    /**
     * For a project-scoped invitation, assigns the now-ACTIVE member to the target project with the
     * target role. The project's {@code project_roles}/{@code project_members} live in the invited org's
     * tenant schema, so this resolves that schema and runs the assignment under that tenant context (in a
     * fresh REQUIRES_NEW transaction inside {@link ProjectAssignmentMaterializer}) regardless of the
     * caller's current JWT context. If the role was deleted since the invite it is skipped gracefully —
     * the member still joins the organization; a pre-existing assignment is left untouched (idempotent).
     */
    private void materializeProjectAssignment(Invitation invitation, Member member) {
        if (!invitation.hasProjectTarget()) {
            return;
        }
        UUID projectId = invitation.getTargetProjectId();
        UUID roleId = invitation.getTargetRoleId();
        String schema = tenantSchemaResolver.resolveTenantSchema(invitation.getOrganizationId().toString());

        TenantContext.runWith(
                new TenantContext.TenantSnapshot(invitation.getOrganizationId().toString(), schema),
                () -> projectAssignmentMaterializer.assign(projectId, roleId, member.getId()));
    }

    private AcceptInvitationResult toResult(Organization organization, Invitation invitation) {
        return new AcceptInvitationResult(
                organization.getId(), organization.getName(), invitation.getMemberId(), invitation.getRole().name());
    }
}
