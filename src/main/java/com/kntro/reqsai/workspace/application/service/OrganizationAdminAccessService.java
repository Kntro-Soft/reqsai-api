package com.kntro.reqsai.workspace.application.service;

import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import com.kntro.reqsai.workspace.domain.model.OrgRole;
import com.kntro.reqsai.workspace.domain.model.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrganizationAdminAccessService {

    private final MemberRepository members;

    public void assertOwnerOrAdmin(Organization organization, UUID requestedBy, String action) {
        OrgRole role = effectiveRole(organization, requestedBy).orElse(null);
        if (role != OrgRole.OWNER && role != OrgRole.ADMIN) {
            throw WorkspaceExceptions.insufficientPermissions(action, requestedBy);
        }
    }

    /**
     * Asserts the caller belongs to the organization (owner or any active member) for read-only
     * access such as viewing the member roster. Management actions still require owner/admin.
     */
    public void assertMember(Organization organization, UUID requestedBy, String action) {
        if (effectiveRole(organization, requestedBy).isEmpty()) {
            throw WorkspaceExceptions.insufficientPermissions(action, requestedBy);
        }
    }

    public boolean isOwnerOrAdmin(Organization organization, UUID requestedBy) {
        OrgRole role = effectiveRole(organization, requestedBy).orElse(null);
        return role == OrgRole.OWNER || role == OrgRole.ADMIN;
    }

    /**
     * Resolves the caller's effective organization role. The organization owner is always {@code OWNER}
     * regardless of any member row; otherwise the caller's role comes from their ACTIVE member record.
     * Empty when the caller is neither the owner nor an active member of the organization.
     */
    public Optional<OrgRole> effectiveRole(Organization organization, UUID requestedBy) {
        if (organization.getOwnerId().equals(requestedBy)) {
            return Optional.of(OrgRole.OWNER);
        }
        return members.findByOrganizationIdAndUserIdAndStatus(
                        organization.getId(), requestedBy, MemberStatus.ACTIVE)
                .map(member -> member.getRole());
    }
}
