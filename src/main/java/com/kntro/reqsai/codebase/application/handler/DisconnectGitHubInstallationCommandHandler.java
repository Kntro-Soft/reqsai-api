package com.kntro.reqsai.codebase.application.handler;

import com.kntro.reqsai.codebase.application.command.DisconnectGitHubInstallationCommand;
import com.kntro.reqsai.codebase.application.port.CodeHostInstallationRepository;
import com.kntro.reqsai.codebase.application.port.CodeRepositoryRepository;
import com.kntro.reqsai.codebase.domain.exception.CodebaseExceptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Unlinks an installation from the organization. Its repositories keep their module map (the copilot still
 * knows the code as last indexed) but stop updating, and say why. Uninstalling the App is done on GitHub.
 */
@Component
@RequiredArgsConstructor
public class DisconnectGitHubInstallationCommandHandler {

    static final String UNLINKED = "Se desconectó GitHub de la organización; vuelve a conectarlo para actualizar"
            + " este repositorio.";

    private final CodeHostInstallationRepository installations;
    private final CodeRepositoryRepository repositories;

    @Transactional
    public void handle(DisconnectGitHubInstallationCommand command) {
        var installation = installations.findByOrganizationIdAndInstallationId(command.organizationId(),
                        command.installationId())
                .orElseThrow(() -> CodebaseExceptions.installationNotFound(command.installationId()));
        installations.delete(installation);
        Instant now = Instant.now();
        repositories.findAllByInstallationId(command.installationId()).forEach(repo -> {
            repo.revokeAccess(UNLINKED, now);
            repositories.save(repo);
        });
    }
}
