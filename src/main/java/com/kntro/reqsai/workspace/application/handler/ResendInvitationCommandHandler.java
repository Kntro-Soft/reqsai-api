package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.ResendInvitationCommand;
import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.service.InvitationIssuer;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import com.kntro.reqsai.workspace.domain.model.Organization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Resends the invitation for a PENDING member (owner/admin only). Supersedes the member's current
 * active invitation and issues a fresh one — new token, new expiry — which re-raises
 * {@code MemberInvitedEvent} so a new email goes out. Only valid while the member is PENDING.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResendInvitationCommandHandler {

    private final OrganizationRepository organizations;
    private final MemberRepository members;
    private final InvitationIssuer invitationIssuer;

    @Transactional
    public Member handle(ResendInvitationCommand command) {
        Organization organization = organizations.findById(command.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(command.organizationId()));

        Member member = members.findByIdAndOrganizationIdAndStatusIn(
                        command.memberId(), command.organizationId(),
                        List.of(MemberStatus.PENDING, MemberStatus.ACTIVE))
                .orElseThrow(() -> WorkspaceExceptions.memberNotFound(command.memberId()));

        if (member.getStatus() != MemberStatus.PENDING) {
            throw WorkspaceExceptions.memberNotPending(command.memberId());
        }

        invitationIssuer.issueFor(organization, member, command.requestedBy());
        log.info("Invitation resent for member {} (org {})", member.getId(), command.organizationId());
        return member;
    }
}
