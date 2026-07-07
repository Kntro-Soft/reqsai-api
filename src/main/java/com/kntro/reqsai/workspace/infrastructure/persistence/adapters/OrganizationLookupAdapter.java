package com.kntro.reqsai.workspace.infrastructure.persistence.adapters;

import com.kntro.reqsai.iam.application.port.OrganizationLookupPort;
import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import com.kntro.reqsai.workspace.infrastructure.persistence.repositories.OrganizationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Adapts the IAM {@link OrganizationLookupPort} to the workspace JPA repositories. */
@Repository
@RequiredArgsConstructor
public class OrganizationLookupAdapter implements OrganizationLookupPort {

    private final OrganizationJpaRepository jpa;
    private final MemberRepository members;

    @Override
    public Optional<UUID> findDefaultOrganizationId(UUID userId) {
        Optional<UUID> owned = jpa.findFirstByOwnerIdOrderByCreatedAtDesc(userId).map(org -> org.getId());
        if (owned.isPresent()) {
            return owned;
        }
        return members.findAllByUserIdAndStatus(userId, MemberStatus.ACTIVE).stream()
                .map(Member::getOrganizationId)
                .findFirst();
    }

    @Override
    public boolean canAccess(UUID organizationId, UUID userId) {
        return jpa.existsByIdAndOwnerId(organizationId, userId)
                || members.existsByOrganizationIdAndUserIdAndStatus(organizationId, userId, MemberStatus.ACTIVE);
    }
}
