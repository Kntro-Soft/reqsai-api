package com.kntro.reqsai.discovery.interfaces.rest.controllers;

import com.kntro.reqsai.discovery.application.handler.CreateDiscoverySessionCommandHandler;
import com.kntro.reqsai.discovery.application.handler.GetProjectSessionQueryHandler;
import com.kntro.reqsai.discovery.application.handler.ListProjectSessionsQueryHandler;
import com.kntro.reqsai.discovery.application.query.GetProjectSessionQuery;
import com.kntro.reqsai.discovery.application.query.ListProjectSessionsQuery;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.interfaces.rest.dto.request.CreateDiscoverySessionRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.DiscoverySessionResponse;
import com.kntro.reqsai.discovery.interfaces.rest.mappers.request.DiscoverySessionRequestMapper;
import com.kntro.reqsai.discovery.interfaces.rest.mappers.response.DiscoverySessionResponseMapper;
import com.kntro.reqsai.discovery.interfaces.rest.swagger.ProjectSessionController;
import com.kntro.reqsai.shared.interfaces.pagination.PageCriteria;
import com.kntro.reqsai.shared.interfaces.pagination.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/** Implementation of the {@link ProjectSessionController} API contract. */
@RestController
@RequiredArgsConstructor
public class ProjectSessionControllerImpl implements ProjectSessionController {

    private final CreateDiscoverySessionCommandHandler createSession;
    private final GetProjectSessionQueryHandler getSession;
    private final ListProjectSessionsQueryHandler listSessions;

    @Override
    public ResponseEntity<DiscoverySessionResponse> create(UUID projectId, CreateDiscoverySessionRequest request) {
        DiscoverySession session = createSession.handle(DiscoverySessionRequestMapper.toCommand(projectId, request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(session.getId())
                .toUri();
        return ResponseEntity.created(location).body(DiscoverySessionResponseMapper.toResponse(session));
    }

    @Override
    public ResponseEntity<DiscoverySessionResponse> getById(UUID projectId, UUID sessionId) {
        DiscoverySession session = getSession.handle(new GetProjectSessionQuery(projectId, sessionId));
        return ResponseEntity.ok(DiscoverySessionResponseMapper.toResponse(session));
    }

    @Override
    public ResponseEntity<PageResponse<DiscoverySessionResponse>> list(UUID projectId, Integer page, Integer size, String sortBy, String sortDirection) {
        PageResponse<DiscoverySessionResponse> response = PageResponse.of(
                listSessions.handle(new ListProjectSessionsQuery(
                        projectId, PageCriteria.of(page, size, sortBy, sortDirection)))
                        .map(DiscoverySessionResponseMapper::toResponse));
        return ResponseEntity.ok(response);
    }
}
