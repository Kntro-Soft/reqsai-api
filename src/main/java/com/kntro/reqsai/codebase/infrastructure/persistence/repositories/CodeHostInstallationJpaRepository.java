package com.kntro.reqsai.codebase.infrastructure.persistence.repositories;

import com.kntro.reqsai.codebase.domain.model.CodeHostInstallation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CodeHostInstallationJpaRepository extends JpaRepository<CodeHostInstallation, UUID> {

    List<CodeHostInstallation> findAllByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);

    Optional<CodeHostInstallation> findByOrganizationIdAndInstallationId(UUID organizationId, long installationId);

    List<CodeHostInstallation> findAllByInstallationId(long installationId);
}
