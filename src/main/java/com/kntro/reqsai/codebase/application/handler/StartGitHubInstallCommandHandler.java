package com.kntro.reqsai.codebase.application.handler;

import com.kntro.reqsai.codebase.application.command.StartGitHubInstallCommand;
import com.kntro.reqsai.codebase.application.port.GitHubAppPort;
import com.kntro.reqsai.codebase.application.service.GitHubInstallState;
import com.kntro.reqsai.codebase.domain.exception.CodebaseExceptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The GitHub page where the organization installs the App, carrying a signed state back to ReqsAI. */
@Component
@RequiredArgsConstructor
public class StartGitHubInstallCommandHandler {

    private final GitHubAppPort app;
    private final GitHubInstallState state;

    public String handle(StartGitHubInstallCommand command) {
        if (!app.isConfigured()) throw CodebaseExceptions.appNotConfigured();
        return app.installUrl(state.issue(command.organizationId(), command.userId()));
    }
}
