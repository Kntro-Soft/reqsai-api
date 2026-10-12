package com.kntro.reqsai.codebase.application.port;

import com.kntro.reqsai.codebase.domain.model.CodeHostInstallation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The GitHub App installations organizations connected (public schema). */
public interface CodeHostInstallationRepository {

    CodeHostInstallation save(CodeHostInstallation installation);

    /** The organization's installations, oldest first. */
    List<CodeHostInstallation> findAllByOrganizationId(UUID organizationId);

    Optional<CodeHostInstallation> findByOrganizationIdAndInstallationId(UUID organizationId, long installationId);

    /** Every organization an installation serves (a GitHub account may be linked to several). */
    List<CodeHostInstallation> findAllByInstallationId(long installationId);

    void delete(CodeHostInstallation installation);
}
