package com.kntro.reqsai.workspace.application.service;

import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import com.kntro.reqsai.workspace.domain.model.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrganizationAdminAccessService {

    private final MemberRepository members;

    public void assertOwnerOrAdmin(Organization organization, UUID requestedBy, String action) {
        if (organization.getOwnerId().equals(requestedBy)) {
            return;
        }
        boolean admin = members.findByOrganizationIdAndUserIdAndStatus(
                        organization.getId(), requestedBy, MemberStatus.ACTIVE)
                .map(member -> member.isAdmin())
                .orElse(false);
        if (!admin) {
            throw WorkspaceExceptions.insufficientPermissions(action, requestedBy);
        }
    }
}
