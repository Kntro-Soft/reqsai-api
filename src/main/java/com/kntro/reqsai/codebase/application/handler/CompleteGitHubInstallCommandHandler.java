package com.kntro.reqsai.codebase.application.handler;

import com.kntro.reqsai.codebase.application.command.CompleteGitHubInstallCommand;
import com.kntro.reqsai.codebase.application.port.CodeHostInstallationRepository;
import com.kntro.reqsai.codebase.application.port.GitHubAppPort;
import com.kntro.reqsai.codebase.application.port.GitHubAppPort.Installation;
import com.kntro.reqsai.codebase.application.result.GitHubInstallResult;
import com.kntro.reqsai.codebase.application.service.GitHubInstallState;
import com.kntro.reqsai.codebase.domain.exception.CodebaseExceptions;
import com.kntro.reqsai.codebase.domain.model.CodeHostInstallation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Links the installation GitHub redirected back with to the organization. The installation id in the URL is
 * not trusted on its own (anyone can type one): a new link needs the signed state of this organization and
 * user, and the OAuth code GitHub adds to the redirect must show that the GitHub user can access that
 * installation. An installation the organization already linked is only refreshed (GitHub redirects with
 * {@code setup_action=update} when the account changes which repositories it shares).
 */
@Component
@RequiredArgsConstructor
public class CompleteGitHubInstallCommandHandler {

    private final CodeHostInstallationRepository installations;
    private final GitHubAppPort app;
    private final GitHubInstallState state;

    @Transactional
    public GitHubInstallResult handle(CompleteGitHubInstallCommand command) {
        if (!app.isConfigured()) throw CodebaseExceptions.appNotConfigured();
        if ("request".equals(command.setupAction()) || command.installationId() == null) {
            if (command.state() != null) state.verify(command.state(), command.organizationId(), command.userId());
            return new GitHubInstallResult(GitHubInstallResult.Status.REQUESTED, null);
        }
        long installationId = command.installationId();
        Optional<CodeHostInstallation> existing = installations.findByOrganizationIdAndInstallationId(
                command.organizationId(), installationId);
        if (existing.isEmpty()) {
            state.verify(command.state(), command.organizationId(), command.userId());
            if (command.code() == null || command.code().isBlank()) {
                throw CodebaseExceptions.installationForbidden("GitHub did not say who installed the App");
            }
            if (!app.installationsOfUser(command.code()).contains(installationId)) {
                throw CodebaseExceptions.installationForbidden("the GitHub user cannot access that installation");
            }
        }
        Installation info = app.installation(installationId);
        CodeHostInstallation installation = existing.orElse(null);
        if (installation == null) {
            installation = CodeHostInstallation.link(command.organizationId(), installationId, info.accountLogin(),
                    info.accountType(), info.repositorySelection(), info.manageUrl(), info.suspendedAt());
        } else {
            installation.refresh(info.accountLogin(), info.accountType(), info.repositorySelection(),
                    info.manageUrl(), info.suspendedAt());
        }
        return new GitHubInstallResult(GitHubInstallResult.Status.LINKED, installations.save(installation));
    }
}
