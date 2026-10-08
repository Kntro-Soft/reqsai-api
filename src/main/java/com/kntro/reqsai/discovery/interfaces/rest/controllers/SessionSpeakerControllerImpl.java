package com.kntro.reqsai.discovery.interfaces.rest.controllers;

import com.kntro.reqsai.discovery.application.command.UpdateSessionSpeakerCommand;
import com.kntro.reqsai.discovery.application.handler.ListSessionSpeakersQueryHandler;
import com.kntro.reqsai.discovery.application.handler.UpdateSessionSpeakerCommandHandler;
import com.kntro.reqsai.discovery.application.query.ListSessionSpeakersQuery;
import com.kntro.reqsai.discovery.application.service.SessionSpeakerService;
import com.kntro.reqsai.discovery.interfaces.rest.dto.request.UpdateSessionSpeakerRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.SessionSpeakerResponse;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.SessionSpeakersResponse;
import com.kntro.reqsai.discovery.interfaces.rest.mappers.response.SessionSpeakerResponseMapper;
import com.kntro.reqsai.discovery.interfaces.rest.swagger.SessionSpeakerController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Implementation of the {@link SessionSpeakerController} API contract. */
@RestController
@RequiredArgsConstructor
public class SessionSpeakerControllerImpl implements SessionSpeakerController {

    private final ListSessionSpeakersQueryHandler listSpeakers;
    private final UpdateSessionSpeakerCommandHandler updateSpeaker;

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'SESSION_READ', authentication)")
    public ResponseEntity<SessionSpeakersResponse> list(UUID projectId, UUID sessionId) {
        SessionSpeakerService.Overview overview = listSpeakers.handle(new ListSessionSpeakersQuery(projectId, sessionId));
        return ResponseEntity.ok(SessionSpeakerResponseMapper.toResponse(sessionId, overview.roster(), overview.overlaps()));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'SESSION_RUN', authentication)")
    public ResponseEntity<SessionSpeakerResponse> update(UUID projectId, UUID sessionId, String label,
                                                         UpdateSessionSpeakerRequest request) {
        var speaker = updateSpeaker.handle(new UpdateSessionSpeakerCommand(
                projectId, sessionId, label, request.displayName(), request.side()));
        return ResponseEntity.ok(SessionSpeakerResponseMapper.toResponse(speaker));
    }
}
