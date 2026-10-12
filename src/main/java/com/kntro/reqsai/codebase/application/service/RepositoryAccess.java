package com.kntro.reqsai.codebase.application.service;

import com.kntro.reqsai.codebase.application.port.CodeHostInstallationRepository;
import com.kntro.reqsai.codebase.application.port.GitHubAppPort;
import com.kntro.reqsai.codebase.domain.exception.CodebaseExceptions;
import com.kntro.reqsai.codebase.domain.model.CodeHostInstallation;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * How the current organization reads a repository: through one of its GitHub App installations, or
 * anonymously for a public repository. An installation is only used while it is linked to the organization
 * of the current tenant and not suspended, so an organization never reads through another's installation.
 */
@Component
@RequiredArgsConstructor
public class RepositoryAccess {

    private final CodeHostInstallationRepository installations;
    private final GitHubAppPort app;

    /** The current organization (the tenant id is the organization id). */
    public static UUID currentOrganization() {
        String tenant = TenantContext.getCurrentTenant();
        if (tenant == null || tenant.isBlank() || TenantContext.DEFAULT_SCHEMA.equals(tenant)) {
            throw new IllegalStateException("No organization bound to the current thread");
        }
        return UUID.fromString(tenant);
    }

    public List<CodeHostInstallation> installations() {
        return installations.findAllByOrganizationId(currentOrganization());
    }

    /** The organization's installation, or {@code CODE_HOST_INSTALLATION_NOT_FOUND}. */
    public CodeHostInstallation linked(long installationId) {
        return installations.findByOrganizationIdAndInstallationId(currentOrganization(), installationId)
                .orElseThrow(() -> CodebaseExceptions.installationNotFound(installationId));
    }

    /** The organization's installation that shares {@code owner/name}, when there is one. */
    public Optional<Long> installationFor(String owner, String name) {
        if (!app.isConfigured()) return Optional.empty();
        return installations().stream()
                .filter(i -> !i.isSuspended() && i.isAccount(owner))
                .map(CodeHostInstallation::getInstallationId)
                .filter(id -> shares(id, owner, name))
                .findFirst();
    }

    /**
     * Whether the installation shares {@code owner/name} with ReqsAI. Only then does GitHub send its pushes,
     * so a repository is read through an installation only when it is one of its repositories.
     */
    public boolean shares(long installationId, String owner, String name) {
        return app.repositories(installationId).stream()
                .anyMatch(r -> r.owner().equalsIgnoreCase(owner) && r.name().equalsIgnoreCase(name));
    }

    /** A token for the installation, or null to read anonymously. */
    public @Nullable String tokenFor(@Nullable Long installationId) {
        if (installationId == null) return null;
        CodeHostInstallation installation = linked(installationId);
        if (installation.isSuspended()) throw CodebaseExceptions.accessDenied();
        return app.token(installationId);
    }
}
