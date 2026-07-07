package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.Exceptions;
import com.kntro.reqsai.shared.domain.valueobjects.Email;
import com.kntro.reqsai.workspace.application.command.BatchInviteMembersCommand;
import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.service.InvitationIssuer;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import com.kntro.reqsai.workspace.domain.model.OrgRole;
import com.kntro.reqsai.workspace.domain.model.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Invites several members atomically. Authorization mirrors the single invite (OWNER/ADMIN only, may only
 * invite ADMIN/MEMBER). Every invitation is validated before anything is written; a duplicate email
 * (within the batch or already present in the org) or any invalid entry fails the whole request and
 * identifies the offending email, so the transaction rolls back and no member is created.
 */
@Component
@RequiredArgsConstructor
public class BatchInviteMembersCommandHandler {

    private static final List<MemberStatus> VISIBLE_STATUSES = List.of(MemberStatus.ACTIVE, MemberStatus.PENDING);

    private final OrganizationRepository organizations;
    private final MemberRepository members;
    private final InvitationIssuer invitationIssuer;

    @Transactional
    public List<Member> handle(BatchInviteMembersCommand command) {
        Organization organization = organizations.findById(command.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(command.organizationId()));

        if (command.invitations() == null || command.invitations().isEmpty()) {
            throw Exceptions.invalidValue("invitations", "at least one invitation is required");
        }

        int maxMembers = organization.getPlanLimits().maxMembers();
        Set<String> seen = new HashSet<>();
        List<Member> created = new ArrayList<>(command.invitations().size());

        for (BatchInviteMembersCommand.Invitation invitation : command.invitations()) {
            String normalizedEmail = Email.of(invitation.email()).value();

            if (invitation.role() == OrgRole.OWNER) {
                throw Exceptions.invalidValue("role", "OWNER cannot be assigned for %s".formatted(normalizedEmail));
            }
            if (!seen.add(normalizedEmail)) {
                throw WorkspaceExceptions.memberAlreadyExists(normalizedEmail);
            }
            if (members.existsByOrganizationIdAndEmailAndStatusIn(command.organizationId(), normalizedEmail, VISIBLE_STATUSES)) {
                throw WorkspaceExceptions.memberAlreadyExists(normalizedEmail);
            }

            Member member = new Member(
                    command.organizationId(),
                    null,
                    normalizedEmail,
                    invitation.displayName(),
                    invitation.role(),
                    MemberStatus.PENDING,
                    command.requestedBy(),
                    Instant.now());
            Member saved = members.save(member);
            invitationIssuer.issueFor(organization, saved, command.requestedBy());
            created.add(saved);
        }

        if (maxMembers != -1) {
            int activeCount = members.countByOrganizationIdAndStatus(command.organizationId(), MemberStatus.ACTIVE);
            if (activeCount > maxMembers) {
                throw WorkspaceExceptions.memberPlanLimitExceeded(maxMembers);
            }
        }

        return created;
    }
}
