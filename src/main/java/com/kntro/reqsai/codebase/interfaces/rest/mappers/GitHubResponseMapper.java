package com.kntro.reqsai.codebase.interfaces.rest.mappers;

import com.kntro.reqsai.codebase.application.result.AvailableRepository;
import com.kntro.reqsai.codebase.application.result.GitHubConnection;
import com.kntro.reqsai.codebase.application.result.GitHubInstallResult;
import com.kntro.reqsai.codebase.domain.model.CodeHostInstallation;
import com.kntro.reqsai.codebase.interfaces.rest.dto.response.GitHubConnectionResponse;
import com.kntro.reqsai.codebase.interfaces.rest.dto.response.GitHubInstallResultResponse;
import com.kntro.reqsai.codebase.interfaces.rest.dto.response.GitHubInstallationResponse;
import com.kntro.reqsai.codebase.interfaces.rest.dto.response.GitHubRepositoryResponse;

/** Maps the GitHub connection results to their response DTOs. */
public final class GitHubResponseMapper {

    private GitHubResponseMapper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static GitHubConnectionResponse toResponse(GitHubConnection connection) {
        return new GitHubConnectionResponse(connection.available(),
                connection.installations().stream().map(GitHubResponseMapper::toResponse).toList());
    }

    public static GitHubInstallationResponse toResponse(CodeHostInstallation installation) {
        return new GitHubInstallationResponse(installation.getInstallationId(), installation.getAccountLogin(),
                installation.getAccountType(), installation.getRepositorySelection(), installation.getManageUrl(),
                installation.isSuspended(), installation.getCreatedAt());
    }

    public static GitHubInstallResultResponse toResponse(GitHubInstallResult result) {
        return new GitHubInstallResultResponse(result.status().name(),
                result.installation() == null ? null : toResponse(result.installation()));
    }

    public static GitHubRepositoryResponse toResponse(AvailableRepository available) {
        var repo = available.repository();
        return new GitHubRepositoryResponse(available.installationId(), repo.owner(), repo.name(),
                repo.owner() + "/" + repo.name(), repo.defaultBranch(), repo.htmlUrl(), repo.isPrivate(),
                repo.description(), repo.pushedAt(), available.connected());
    }
}
