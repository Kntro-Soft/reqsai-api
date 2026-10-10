package com.kntro.reqsai.codebase.application.handler;

import com.kntro.reqsai.codebase.application.port.CodeHostInstallationRepository;
import com.kntro.reqsai.codebase.application.port.GitHubAppPort;
import com.kntro.reqsai.codebase.application.query.GetGitHubConnectionQuery;
import com.kntro.reqsai.codebase.application.result.GitHubConnection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Whether GitHub can be connected on this server, and the installations the organization linked. */
@Component
@RequiredArgsConstructor
public class GetGitHubConnectionQueryHandler {

    private final CodeHostInstallationRepository installations;
    private final GitHubAppPort app;

    @Transactional(readOnly = true)
    public GitHubConnection handle(GetGitHubConnectionQuery query) {
        if (!app.isConfigured()) return new GitHubConnection(false, java.util.List.of());
        return new GitHubConnection(true, installations.findAllByOrganizationId(query.organizationId()));
    }
}
