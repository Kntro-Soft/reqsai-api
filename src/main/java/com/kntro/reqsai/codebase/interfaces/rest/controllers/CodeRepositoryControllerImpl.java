package com.kntro.reqsai.codebase.interfaces.rest.controllers;

import com.kntro.reqsai.codebase.application.command.ConnectRepositoryCommand;
import com.kntro.reqsai.codebase.application.command.DisconnectRepositoryCommand;
import com.kntro.reqsai.codebase.application.command.ReindexRepositoryCommand;
import com.kntro.reqsai.codebase.application.handler.ConnectRepositoryCommandHandler;
import com.kntro.reqsai.codebase.application.handler.DisconnectRepositoryCommandHandler;
import com.kntro.reqsai.codebase.application.handler.ListModulesQueryHandler;
import com.kntro.reqsai.codebase.application.handler.ListRepositoriesQueryHandler;
import com.kntro.reqsai.codebase.application.handler.ReindexRepositoryCommandHandler;
import com.kntro.reqsai.codebase.application.query.ListModulesQuery;
import com.kntro.reqsai.codebase.application.query.ListRepositoriesQuery;
import com.kntro.reqsai.codebase.interfaces.rest.dto.request.ConnectRepositoryRequest;
import com.kntro.reqsai.codebase.interfaces.rest.dto.response.CodeModuleResponse;
import com.kntro.reqsai.codebase.interfaces.rest.dto.response.CodeRepositoryResponse;
import com.kntro.reqsai.codebase.interfaces.rest.mappers.CodeRepositoryResponseMapper;
import com.kntro.reqsai.codebase.interfaces.rest.swagger.CodeRepositoryController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Implementation of the {@link CodeRepositoryController} API contract. */
@RestController
@RequiredArgsConstructor
public class CodeRepositoryControllerImpl implements CodeRepositoryController {

    private final ConnectRepositoryCommandHandler connect;
    private final ListRepositoriesQueryHandler list;
    private final ReindexRepositoryCommandHandler reindex;
    private final DisconnectRepositoryCommandHandler disconnect;
    private final ListModulesQueryHandler modules;

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'INTEGRATION_WRITE', authentication)")
    public ResponseEntity<CodeRepositoryResponse> connect(UUID projectId, ConnectRepositoryRequest request) {
        var repository = connect.handle(new ConnectRepositoryCommand(projectId, request.repository(),
                request.branch(), request.accessToken()));
        return ResponseEntity.status(HttpStatus.CREATED).body(CodeRepositoryResponseMapper.toResponse(repository));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'INTEGRATION_READ', authentication)")
    public ResponseEntity<List<CodeRepositoryResponse>> list(UUID projectId) {
        return ResponseEntity.ok(list.handle(new ListRepositoriesQuery(projectId)).stream()
                .map(CodeRepositoryResponseMapper::toResponse)
                .toList());
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'INTEGRATION_WRITE', authentication)")
    public ResponseEntity<CodeRepositoryResponse> reindex(UUID projectId, UUID repositoryId) {
        return ResponseEntity.ok(CodeRepositoryResponseMapper.toResponse(
                reindex.handle(new ReindexRepositoryCommand(projectId, repositoryId))));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'INTEGRATION_DELETE', authentication)")
    public ResponseEntity<Void> disconnect(UUID projectId, UUID repositoryId) {
        disconnect.handle(new DisconnectRepositoryCommand(projectId, repositoryId));
        return ResponseEntity.noContent().build();
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'INTEGRATION_READ', authentication)")
    public ResponseEntity<List<CodeModuleResponse>> modules(UUID projectId, UUID repositoryId) {
        var found = modules.handle(new ListModulesQuery(projectId, repositoryId));
        return ResponseEntity.ok(found.modules().stream()
                .map(m -> CodeRepositoryResponseMapper.toResponse(m, found.repository()))
                .toList());
    }
}
