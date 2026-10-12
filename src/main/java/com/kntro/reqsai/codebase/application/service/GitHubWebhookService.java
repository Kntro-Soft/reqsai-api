package com.kntro.reqsai.codebase.application.service;

import com.kntro.reqsai.codebase.application.port.CodeHostInstallationRepository;
import com.kntro.reqsai.codebase.application.port.GitHubAppPort;
import com.kntro.reqsai.codebase.domain.model.CodeHostInstallation;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantSchemaResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Turns GitHub App webhooks into index updates. The installation in the event names the organizations it
 * serves (public registry); each one's repositories are updated bound to its own schema:
 * <ul>
 *   <li>{@code push} to a tracked branch → the repository reindexes (only changed modules are redescribed);</li>
 *   <li>{@code installation_repositories} removed → those repositories stop updating;</li>
 *   <li>{@code installation} deleted → the link disappears and its repositories stop updating; suspended →
 *       likewise until it is unsuspended.</li>
 * </ul>
 * The caller verified the signature. Unknown installations and other events are ignored.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GitHubWebhookService {

    static final String REMOVED = "Este repositorio ya no está compartido con ReqsAI en GitHub; vuelve a darle acceso"
            + " en la app de GitHub para actualizarlo.";
    static final String UNINSTALLED = "Se desinstaló la app de ReqsAI en GitHub; vuelve a conectar GitHub para"
            + " actualizar este repositorio.";
    static final String SUSPENDED = "La app de ReqsAI está suspendida en GitHub; reactívala para actualizar este"
            + " repositorio.";

    private final GitHubAppPort app;
    private final CodeHostInstallationRepository installations;
    private final GitHubEventApplier applier;
    private final TenantSchemaResolver tenants;
    private final ObjectMapper json;

    public boolean verify(byte[] payload, @Nullable String signature) {
        return app.isConfigured() && app.verifySignature(payload, signature);
    }

    public void handle(@Nullable String event, byte[] payload) {
        if (event == null) return;
        JsonNode root = json.readTree(payload);
        long installationId = root.path("installation").path("id").asLong(0);
        if (installationId <= 0) return;
        switch (event) {
            case "push" -> push(installationId, root);
            case "installation_repositories" -> repositoriesChanged(installationId, root);
            case "installation" -> installationChanged(installationId, root.path("action").asString(""));
            default -> log.debug("GitHub webhook {} ignored", event);
        }
    }

    private void push(long installationId, JsonNode root) {
        String ref = root.path("ref").asString("");
        if (!ref.startsWith("refs/heads/") || root.path("deleted").asBoolean(false)) return;
        String branch = ref.substring("refs/heads/".length());
        String commit = root.path("after").asString("");
        JsonNode repository = root.path("repository");
        String owner = repository.path("owner").path("login").asString(repository.path("owner").path("name").asString(""));
        String name = repository.path("name").asString("");
        if (commit.isBlank() || commit.matches("0+") || owner.isBlank() || name.isBlank()) return;
        forEachOrganization(installationId, installation -> {
            int started = applier.push(installationId, owner, name, branch, commit);
            if (started > 0) log.info("Push to {}/{}@{}: {} repositories reindexing", owner, name, branch, started);
        });
    }

    private void repositoriesChanged(long installationId, JsonNode root) {
        Set<String> removed = new LinkedHashSet<>();
        for (JsonNode repo : root.path("repositories_removed")) {
            String fullName = repo.path("full_name").asString("");
            if (!fullName.isBlank()) removed.add(fullName.toLowerCase(Locale.ROOT));
        }
        if (removed.isEmpty()) return;
        forEachOrganization(installationId, installation -> applier.revoke(installationId, removed, REMOVED));
    }

    private void installationChanged(long installationId, String action) {
        switch (action) {
            case "deleted" -> forEachOrganization(installationId, installation -> {
                applier.revoke(installationId, null, UNINSTALLED);
                installations.delete(installation);
            });
            case "suspend" -> forEachOrganization(installationId, installation -> {
                installation.suspend(Instant.now());
                installations.save(installation);
                applier.revoke(installationId, null, SUSPENDED);
            });
            case "unsuspend" -> forEachOrganization(installationId, installation -> {
                installation.unsuspend();
                installations.save(installation);
            });
            default -> log.debug("GitHub installation {} {} ignored", installationId, action);
        }
    }

    /** Runs {@code action} bound to the schema of every organization the installation serves. */
    private void forEachOrganization(long installationId, Consumer<CodeHostInstallation> action) {
        List<CodeHostInstallation> linked = installations.findAllByInstallationId(installationId);
        for (CodeHostInstallation installation : linked) {
            String tenantId = installation.getOrganizationId().toString();
            TenantContext.runWith(new TenantContext.TenantSnapshot(tenantId, tenants.resolveTenantSchema(tenantId)),
                    () -> action.accept(installation));
        }
    }
}
