package com.kntro.reqsai.discovery.infrastructure.persistence.adapters;

import com.kntro.reqsai.discovery.application.port.ShareLinkRepository;
import com.kntro.reqsai.discovery.domain.model.ShareLink;
import com.kntro.reqsai.discovery.infrastructure.persistence.repositories.ShareLinkJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adapts {@link ShareLinkRepository} port to Spring Data JPA. */
@Repository
@RequiredArgsConstructor
public class ShareLinkRepositoryAdapter implements ShareLinkRepository {

    private final ShareLinkJpaRepository jpa;

    @Override
    public ShareLink save(ShareLink link) {
        return jpa.save(link);
    }

    @Override
    public Optional<ShareLink> findByTokenHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash);
    }

    @Override
    public Optional<ShareLink> findByIdAndProject(UUID id, UUID organizationId, UUID projectId) {
        return jpa.findByIdAndOrganizationIdAndProjectId(id, organizationId, projectId);
    }

    @Override
    public List<ShareLink> findAllByProject(UUID organizationId, UUID projectId) {
        return jpa.findAllByOrganizationIdAndProjectIdOrderByCreatedAtDesc(organizationId, projectId);
    }
}
