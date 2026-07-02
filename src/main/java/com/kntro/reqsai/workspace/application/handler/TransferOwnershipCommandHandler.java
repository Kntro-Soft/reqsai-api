package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.TransferOwnershipCommand;
import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import com.kntro.reqsai.workspace.domain.model.OrgRole;
import com.kntro.reqsai.workspace.domain.model.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Transfers organization ownership. Only the current owner may transfer; the target must be an ACTIVE
 * member of the organization. On success the organization's {@code ownerId} becomes the target member's
 * user id and the previous owner is demoted to an ADMIN member row (created if none existed).
 */
@Component
@RequiredArgsConstructor
public class TransferOwnershipCommandHandler {

    private final OrganizationRepository organizations;
    private final MemberRepository members;

    @Transactional
    public Organization handle(TransferOwnershipCommand command) {
        Organization organization = organizations.findById(command.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(command.organizationId()));

        UUID previousOwnerId = organization.getOwnerId();
        if (!previousOwnerId.equals(command.requestedBy())) {
            throw WorkspaceExceptions.insufficientPermissions("transfer organization ownership", command.requestedBy());
        }

        Member target = members.findByIdAndOrganizationIdAndStatusIn(
                        command.newOwnerMemberId(), command.organizationId(), java.util.List.of(MemberStatus.ACTIVE))
                .orElseThrow(() -> WorkspaceExceptions.memberNotFound(command.newOwnerMemberId()));

        UUID newOwnerUserId = target.getUserId();
        if (newOwnerUserId == null) {
            throw WorkspaceExceptions.invalidOwnershipTransfer("target member has no associated user");
        }
        if (newOwnerUserId.equals(previousOwnerId)) {
            throw WorkspaceExceptions.invalidOwnershipTransfer("target member is already the owner");
        }

        organization.transferOwnership(newOwnerUserId);
        organizations.save(organization);

        // Align the new owner's member row to OWNER so its role is never stale. We intentionally keep the
        // row (rather than deleting it): members are soft-deleted across the domain, and a later
        // transfer-away demotes the former owner by finding exactly this row.
        target.changeRole(OrgRole.OWNER);
        members.save(target);

        demotePreviousOwner(organization.getId(), previousOwnerId, command.requestedBy());
        return organization;
    }

    /** Ensures the previous owner keeps access as an ADMIN member (creates the row if it was implicit). */
    private void demotePreviousOwner(UUID organizationId, UUID previousOwnerId, UUID requestedBy) {
        members.findByOrganizationIdAndUserIdAndStatus(organizationId, previousOwnerId, MemberStatus.ACTIVE)
                .ifPresentOrElse(
                        existing -> {
                            existing.changeRole(OrgRole.ADMIN);
                            members.save(existing);
                        },
                        () -> members.save(new Member(
                                organizationId,
                                previousOwnerId,
                                previousOwnerId + "@owner.local",
                                "Former Owner",
                                OrgRole.ADMIN,
                                MemberStatus.ACTIVE,
                                requestedBy,
                                Instant.now())));
    }
}
