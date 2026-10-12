package com.kntro.reqsai.codebase.interfaces.rest.controllers;

import com.kntro.reqsai.codebase.application.command.CompleteGitHubInstallCommand;
import com.kntro.reqsai.codebase.application.command.DisconnectGitHubInstallationCommand;
import com.kntro.reqsai.codebase.application.command.StartGitHubInstallCommand;
import com.kntro.reqsai.codebase.application.handler.CompleteGitHubInstallCommandHandler;
import com.kntro.reqsai.codebase.application.handler.DisconnectGitHubInstallationCommandHandler;
import com.kntro.reqsai.codebase.application.handler.GetGitHubConnectionQueryHandler;
import com.kntro.reqsai.codebase.application.handler.StartGitHubInstallCommandHandler;
import com.kntro.reqsai.codebase.application.query.GetGitHubConnectionQuery;
import com.kntro.reqsai.codebase.interfaces.rest.dto.request.CompleteGitHubInstallRequest;
import com.kntro.reqsai.codebase.interfaces.rest.dto.response.GitHubConnectionResponse;
import com.kntro.reqsai.codebase.interfaces.rest.dto.response.GitHubInstallResultResponse;
import com.kntro.reqsai.codebase.interfaces.rest.dto.response.GitHubInstallUrlResponse;
import com.kntro.reqsai.codebase.interfaces.rest.mappers.GitHubResponseMapper;
import com.kntro.reqsai.codebase.interfaces.rest.swagger.OrganizationGitHubController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Implementation of the {@link OrganizationGitHubController} API contract. */
@RestController
@RequiredArgsConstructor
public class OrganizationGitHubControllerImpl implements OrganizationGitHubController {

    private final GetGitHubConnectionQueryHandler connection;
    private final StartGitHubInstallCommandHandler start;
    private final CompleteGitHubInstallCommandHandler complete;
    private final DisconnectGitHubInstallationCommandHandler disconnect;

    @Override
    @PreAuthorize("@authz.orgOwnerOrAdmin(#orgId, authentication)")
    public ResponseEntity<GitHubConnectionResponse> get(UUID orgId) {
        return ResponseEntity.ok(GitHubResponseMapper.toResponse(connection.handle(new GetGitHubConnectionQuery(orgId))));
    }

    @Override
    @PreAuthorize("@authz.orgOwnerOrAdmin(#orgId, authentication)")
    public ResponseEntity<GitHubInstallUrlResponse> startInstall(UUID orgId, Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(new GitHubInstallUrlResponse(start.handle(new StartGitHubInstallCommand(orgId, userId))));
    }

    @Override
    @PreAuthorize("@authz.orgOwnerOrAdmin(#orgId, authentication)")
    public ResponseEntity<GitHubInstallResultResponse> completeInstall(UUID orgId, CompleteGitHubInstallRequest request,
                                                                       Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        var result = complete.handle(new CompleteGitHubInstallCommand(orgId, userId, request.installationId(),
                request.setupAction(), request.state(), request.code()));
        return ResponseEntity.ok(GitHubResponseMapper.toResponse(result));
    }

    @Override
    @PreAuthorize("@authz.orgOwnerOrAdmin(#orgId, authentication)")
    public ResponseEntity<Void> disconnect(UUID orgId, long installationId) {
        disconnect.handle(new DisconnectGitHubInstallationCommand(orgId, installationId));
        return ResponseEntity.noContent().build();
    }
}
