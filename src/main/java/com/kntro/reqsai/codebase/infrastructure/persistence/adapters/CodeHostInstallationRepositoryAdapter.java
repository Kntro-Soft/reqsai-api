package com.kntro.reqsai.codebase.infrastructure.persistence.adapters;

import com.kntro.reqsai.codebase.application.port.CodeHostInstallationRepository;
import com.kntro.reqsai.codebase.domain.model.CodeHostInstallation;
import com.kntro.reqsai.codebase.infrastructure.persistence.repositories.CodeHostInstallationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adapts the {@link CodeHostInstallationRepository} port to Spring Data JPA. */
@Repository
@RequiredArgsConstructor
public class CodeHostInstallationRepositoryAdapter implements CodeHostInstallationRepository {

    private final CodeHostInstallationJpaRepository jpa;

    @Override
    public CodeHostInstallation save(CodeHostInstallation installation) {
        return jpa.save(installation);
    }

    @Override
    public List<CodeHostInstallation> findAllByOrganizationId(UUID organizationId) {
        return jpa.findAllByOrganizationIdOrderByCreatedAtAsc(organizationId);
    }

    @Override
    public Optional<CodeHostInstallation> findByOrganizationIdAndInstallationId(UUID organizationId,
                                                                               long installationId) {
        return jpa.findByOrganizationIdAndInstallationId(organizationId, installationId);
    }

    @Override
    public List<CodeHostInstallation> findAllByInstallationId(long installationId) {
        return jpa.findAllByInstallationId(installationId);
    }

    @Override
    public void delete(CodeHostInstallation installation) {
        jpa.delete(installation);
    }
}
