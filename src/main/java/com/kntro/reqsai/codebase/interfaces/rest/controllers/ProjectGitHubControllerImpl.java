package com.kntro.reqsai.codebase.interfaces.rest.controllers;

import com.kntro.reqsai.codebase.application.handler.GetGitHubConnectionQueryHandler;
import com.kntro.reqsai.codebase.application.handler.ListGitHubRepositoriesQueryHandler;
import com.kntro.reqsai.codebase.application.query.GetGitHubConnectionQuery;
import com.kntro.reqsai.codebase.application.query.ListGitHubRepositoriesQuery;
import com.kntro.reqsai.codebase.application.service.RepositoryAccess;
import com.kntro.reqsai.codebase.interfaces.rest.dto.response.GitHubConnectionResponse;
import com.kntro.reqsai.codebase.interfaces.rest.dto.response.GitHubRepositoryResponse;
import com.kntro.reqsai.codebase.interfaces.rest.mappers.GitHubResponseMapper;
import com.kntro.reqsai.codebase.interfaces.rest.swagger.ProjectGitHubController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Implementation of the {@link ProjectGitHubController} API contract. */
@RestController
@RequiredArgsConstructor
public class ProjectGitHubControllerImpl implements ProjectGitHubController {

    private final GetGitHubConnectionQueryHandler connection;
    private final ListGitHubRepositoriesQueryHandler repositories;

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'INTEGRATION_READ', authentication)")
    public ResponseEntity<GitHubConnectionResponse> get(UUID projectId) {
        return ResponseEntity.ok(GitHubResponseMapper.toResponse(
                connection.handle(new GetGitHubConnectionQuery(RepositoryAccess.currentOrganization()))));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'INTEGRATION_WRITE', authentication)")
    public ResponseEntity<List<GitHubRepositoryResponse>> repositories(UUID projectId) {
        return ResponseEntity.ok(repositories.handle(new ListGitHubRepositoriesQuery(projectId)).stream()
                .map(GitHubResponseMapper::toResponse)
                .toList());
    }
}
