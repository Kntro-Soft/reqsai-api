package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.Exceptions;
import com.kntro.reqsai.workspace.application.command.ChangeMemberStatusCommand;
import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.service.OrganizationAdminAccessService;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import com.kntro.reqsai.workspace.domain.model.OrgRole;
import com.kntro.reqsai.workspace.domain.model.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Deactivates (ACTIVE to INACTIVE) or reactivates (INACTIVE to ACTIVE) an organization member. RBAC
 * mirrors the role change: OWNER may change any non-owner member; ADMIN may change MEMBER rows only, never
 * their own row, another ADMIN, nor the OWNER member. Reactivation requires the member to still carry a
 * user id (it was an active membership before being deactivated).
 */
@Component
@RequiredArgsConstructor
public class ChangeMemberStatusCommandHandler {

    private static final List<MemberStatus> MANAGEABLE_STATUSES =
            List.of(MemberStatus.ACTIVE, MemberStatus.INACTIVE);

    private final OrganizationRepository organizations;
    private final MemberRepository members;
    private final OrganizationAdminAccessService access;

    @Transactional
    public Member handle(ChangeMemberStatusCommand command) {
        if (command.status() != MemberStatus.ACTIVE && command.status() != MemberStatus.INACTIVE) {
            throw Exceptions.invalidValue("status", "only ACTIVE or INACTIVE may be set via this endpoint");
        }

        Organization organization = organizations.findById(command.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(command.organizationId()));

        OrgRole callerRole = access.effectiveRole(organization, command.requestedBy())
                .orElseThrow(() -> WorkspaceExceptions.insufficientPermissions(
                        "change organization member status", command.requestedBy()));

        Member target = members.findByIdAndOrganizationIdAndStatusIn(
                        command.memberId(), command.organizationId(), MANAGEABLE_STATUSES)
                .orElseThrow(() -> WorkspaceExceptions.memberNotFound(command.memberId()));

        authorize(callerRole, command.requestedBy(), target);

        if (command.status() == MemberStatus.INACTIVE) {
            target.deactivate();
        } else {
            UUID userId = target.getUserId();
            if (userId == null) {
                throw Exceptions.invalidValue("status", "a member without a user id cannot be reactivated");
            }
            target.reactivate(userId);
        }
        return members.save(target);
    }

    private void authorize(OrgRole callerRole, UUID requestedBy, Member target) {
        if (target.getRole() == OrgRole.OWNER) {
            throw WorkspaceExceptions.insufficientPermissions("change the OWNER member status", requestedBy);
        }
        switch (callerRole) {
            case OWNER -> {
                // Owner may change any non-owner member.
            }
            case ADMIN -> {
                boolean changingSelf = requestedBy.equals(target.getUserId());
                if (changingSelf || target.getRole() == OrgRole.ADMIN) {
                    throw WorkspaceExceptions.insufficientPermissions("change this member status", requestedBy);
                }
            }
            default -> throw WorkspaceExceptions.insufficientPermissions(
                    "change organization member status", requestedBy);
        }
    }
}
