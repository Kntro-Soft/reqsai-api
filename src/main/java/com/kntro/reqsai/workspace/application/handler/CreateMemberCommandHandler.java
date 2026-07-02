package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.Exceptions;
import com.kntro.reqsai.workspace.application.command.CreateMemberCommand;
import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.service.InvitationIssuer;
import com.kntro.reqsai.workspace.application.service.OrganizationAdminAccessService;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import com.kntro.reqsai.workspace.domain.model.OrgRole;
import com.kntro.reqsai.workspace.domain.model.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CreateMemberCommandHandler {

    private static final List<MemberStatus> VISIBLE_STATUSES = List.of(MemberStatus.ACTIVE, MemberStatus.PENDING);

    private final OrganizationRepository organizations;
    private final MemberRepository members;
    private final OrganizationAdminAccessService access;
    private final InvitationIssuer invitationIssuer;

    @Transactional
    public Member handle(CreateMemberCommand command) {
        Organization organization = organizations.findById(command.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(command.organizationId()));
        access.assertOwnerOrAdmin(organization, command.requestedBy(), "manage organization members");

        if (command.role() == OrgRole.OWNER) {
            throw Exceptions.invalidValue("role", "OWNER cannot be assigned via this endpoint");
        }

        if (members.existsByOrganizationIdAndEmailAndStatusIn(command.organizationId(), command.email(), VISIBLE_STATUSES)) {
            throw WorkspaceExceptions.memberAlreadyExists(command.email());
        }

        MemberStatus status = command.userId() != null ? MemberStatus.ACTIVE : MemberStatus.PENDING;
        if (status == MemberStatus.ACTIVE) {
            if (members.existsByOrganizationIdAndUserIdAndStatus(command.organizationId(), command.userId(), MemberStatus.ACTIVE)) {
                throw WorkspaceExceptions.memberAlreadyExists(command.userId().toString());
            }
            int maxMembers = organization.getPlanLimits().maxMembers();
            if (maxMembers != -1 && members.countByOrganizationIdAndStatus(command.organizationId(), MemberStatus.ACTIVE) >= maxMembers) {
                throw WorkspaceExceptions.memberPlanLimitExceeded(maxMembers);
            }
        }

        Member member = new Member(
                command.organizationId(),
                command.userId(),
                command.email(),
                command.displayName(),
                command.role(),
                status,
                command.requestedBy(),
                Instant.now());
        Member saved = members.save(member);

        // A PENDING member (no linked user yet) gets a tokenized invitation + email; an ACTIVE member
        // created with a known userId (already inside the org) does not need one.
        if (status == MemberStatus.PENDING) {
            invitationIssuer.issueFor(organization, saved, command.requestedBy());
        }
        return saved;
    }
}
