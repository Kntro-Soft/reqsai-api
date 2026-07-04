package com.kntro.reqsai.discovery.interfaces.rest.controllers;

import com.kntro.reqsai.discovery.application.command.PauseRecordingCommand;
import com.kntro.reqsai.discovery.application.command.ResumeRecordingCommand;
import com.kntro.reqsai.discovery.application.command.StartRecordingCommand;
import com.kntro.reqsai.discovery.application.command.StopRecordingCommand;
import com.kntro.reqsai.discovery.application.handler.CreateDiscoverySessionCommandHandler;
import com.kntro.reqsai.discovery.application.handler.GetProjectSessionQueryHandler;
import com.kntro.reqsai.discovery.application.handler.ListProjectSessionsQueryHandler;
import com.kntro.reqsai.discovery.application.handler.PauseRecordingCommandHandler;
import com.kntro.reqsai.discovery.application.handler.ResumeRecordingCommandHandler;
import com.kntro.reqsai.discovery.application.handler.StartRecordingCommandHandler;
import com.kntro.reqsai.discovery.application.handler.StopRecordingCommandHandler;
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
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final StartRecordingCommandHandler startRecording;
    private final PauseRecordingCommandHandler pauseRecording;
    private final ResumeRecordingCommandHandler resumeRecording;
    private final StopRecordingCommandHandler stopRecording;

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'SESSION_RUN', authentication)")
    public ResponseEntity<DiscoverySessionResponse> create(UUID projectId, CreateDiscoverySessionRequest request) {
        DiscoverySession session = createSession.handle(DiscoverySessionRequestMapper.toCommand(projectId, request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(session.getId())
                .toUri();
        return ResponseEntity.created(location).body(DiscoverySessionResponseMapper.toResponse(session));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'SESSION_READ', authentication)")
    public ResponseEntity<DiscoverySessionResponse> getById(UUID projectId, UUID sessionId) {
        DiscoverySession session = getSession.handle(new GetProjectSessionQuery(projectId, sessionId));
        return ResponseEntity.ok(DiscoverySessionResponseMapper.toResponse(session));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'SESSION_READ', authentication)")
    public ResponseEntity<PageResponse<DiscoverySessionResponse>> list(UUID projectId, Integer page, Integer size, String sortBy, String sortDirection) {
        PageResponse<DiscoverySessionResponse> response = PageResponse.of(
                listSessions.handle(new ListProjectSessionsQuery(
                        projectId, PageCriteria.of(page, size, sortBy, sortDirection)))
                        .map(DiscoverySessionResponseMapper::toResponse));
        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'SESSION_RUN', authentication)")
    public ResponseEntity<DiscoverySessionResponse> start(UUID projectId, UUID sessionId) {
        DiscoverySession session = startRecording.handle(new StartRecordingCommand(projectId, sessionId));
        return ResponseEntity.ok(DiscoverySessionResponseMapper.toResponse(session));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'SESSION_RUN', authentication)")
    public ResponseEntity<DiscoverySessionResponse> pause(UUID projectId, UUID sessionId) {
        DiscoverySession session = pauseRecording.handle(new PauseRecordingCommand(projectId, sessionId));
        return ResponseEntity.ok(DiscoverySessionResponseMapper.toResponse(session));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'SESSION_RUN', authentication)")
    public ResponseEntity<DiscoverySessionResponse> resume(UUID projectId, UUID sessionId) {
        DiscoverySession session = resumeRecording.handle(new ResumeRecordingCommand(projectId, sessionId));
        return ResponseEntity.ok(DiscoverySessionResponseMapper.toResponse(session));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'SESSION_RUN', authentication)")
    public ResponseEntity<DiscoverySessionResponse> stop(UUID projectId, UUID sessionId) {
        DiscoverySession session = stopRecording.handle(new StopRecordingCommand(projectId, sessionId));
        return ResponseEntity.ok(DiscoverySessionResponseMapper.toResponse(session));
    }
}
