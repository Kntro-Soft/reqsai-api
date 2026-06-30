package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.ChangeMemberRoleCommand;
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

/**
 * Changes an organization member's role. The RBAC rules mirror Vercel/GitHub organization semantics:
 * <ul>
 *   <li>The {@code OWNER} role is immutable — it can never be assigned here, and an OWNER member's role
 *       can never be changed here (ownership transfer is out of scope).</li>
 *   <li>An {@code OWNER} caller may promote MEMBER&rarr;ADMIN or demote ADMIN&rarr;MEMBER.</li>
 *   <li>An {@code ADMIN} caller may change MEMBER roles only — never their own role, another ADMIN's,
 *       nor the OWNER's.</li>
 *   <li>A {@code MEMBER} caller may not change any role.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ChangeMemberRoleCommandHandler {

    private static final List<MemberStatus> MUTABLE_STATUSES = List.of(MemberStatus.ACTIVE, MemberStatus.PENDING);

    private final OrganizationRepository organizations;
    private final MemberRepository members;
    private final OrganizationAdminAccessService access;

    @Transactional
    public Member handle(ChangeMemberRoleCommand command) {
        Organization organization = organizations.findById(command.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(command.organizationId()));

        OrgRole callerRole = access.effectiveRole(organization, command.requestedBy())
                .orElseThrow(() -> WorkspaceExceptions.insufficientPermissions(
                        "change organization member roles", command.requestedBy()));

        if (command.role() == OrgRole.OWNER) {
            throw WorkspaceExceptions.insufficientPermissions(
                    "assign the OWNER role", command.requestedBy());
        }

        Member target = members.findByIdAndOrganizationIdAndStatusIn(
                        command.memberId(), command.organizationId(), MUTABLE_STATUSES)
                .orElseThrow(() -> WorkspaceExceptions.memberNotFound(command.memberId()));

        authorize(callerRole, command.requestedBy(), target);

        target.changeRole(command.role());
        return members.save(target);
    }

    private void authorize(OrgRole callerRole, java.util.UUID requestedBy, Member target) {
        // The OWNER role is immutable: an OWNER member can never be changed here.
        if (target.getRole() == OrgRole.OWNER) {
            throw WorkspaceExceptions.insufficientPermissions(
                    "change the OWNER member role", requestedBy);
        }

        switch (callerRole) {
            case OWNER -> {
                // Owner may change any non-owner member (MEMBER <-> ADMIN).
            }
            case ADMIN -> {
                boolean changingSelf = requestedBy.equals(target.getUserId());
                if (changingSelf || target.getRole() == OrgRole.ADMIN) {
                    throw WorkspaceExceptions.insufficientPermissions(
                            "change this member role", requestedBy);
                }
                // Admin may change MEMBER roles only (target is MEMBER here).
            }
            default -> throw WorkspaceExceptions.insufficientPermissions(
                    "change organization member roles", requestedBy);
        }
    }
}
