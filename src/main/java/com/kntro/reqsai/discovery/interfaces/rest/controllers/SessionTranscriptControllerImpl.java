package com.kntro.reqsai.discovery.interfaces.rest.controllers;

import com.kntro.reqsai.discovery.application.command.StartDiscoveryProcessingCommand;
import com.kntro.reqsai.discovery.application.command.UploadTranscriptCommand;
import com.kntro.reqsai.shared.interfaces.rest.FileUploadUtils;
import com.kntro.reqsai.discovery.application.handler.GetSessionTranscriptQueryHandler;
import com.kntro.reqsai.discovery.application.handler.StartDiscoveryProcessingCommandHandler;
import com.kntro.reqsai.discovery.application.handler.UploadTranscriptCommandHandler;
import com.kntro.reqsai.discovery.application.query.GetSessionTranscriptQuery;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.DiscoverySessionResponse;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.ProcessTranscriptResponse;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.TranscriptResponse;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.UserStoryResponse;
import com.kntro.reqsai.discovery.interfaces.rest.mappers.response.DiscoverySessionResponseMapper;
import com.kntro.reqsai.discovery.interfaces.rest.mappers.response.UserStoryResponseMapper;
import com.kntro.reqsai.discovery.interfaces.rest.swagger.SessionTranscriptController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** Implementation of the {@link SessionTranscriptController} API contract. */
@RestController
@RequiredArgsConstructor
public class SessionTranscriptControllerImpl implements SessionTranscriptController {

    private final UploadTranscriptCommandHandler uploadTranscript;
    private final StartDiscoveryProcessingCommandHandler processTranscript;
    private final GetSessionTranscriptQueryHandler getTranscript;

    @Override
    @PreAuthorize("@discoveryAuthz.sessionPermission(#sessionId, 'SESSION_RUN', authentication)")
    public ResponseEntity<DiscoverySessionResponse> upload(UUID sessionId, MultipartFile file) {
        byte[] audioBytes = FileUploadUtils.readBytes(file);
        DiscoverySession session = uploadTranscript.handle(new UploadTranscriptCommand(sessionId, audioBytes, file.getOriginalFilename()));
        DiscoverySessionResponse response = DiscoverySessionResponseMapper.toResponse(session);
        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("@discoveryAuthz.sessionPermission(#sessionId, 'SESSION_RUN', authentication)")
    public ResponseEntity<ProcessTranscriptResponse> process(UUID sessionId) {
        StartDiscoveryProcessingCommandHandler.ProcessingResult result = processTranscript.handle(new StartDiscoveryProcessingCommand(sessionId));
        List<UserStoryResponse> storyResponses = result.stories().stream()
                .map(UserStoryResponseMapper::toResponse).toList();
        DiscoverySessionResponse sessionResponse = DiscoverySessionResponseMapper.toResponse(result.session());
        return ResponseEntity.ok(new ProcessTranscriptResponse(sessionResponse, storyResponses));
    }

    @Override
    @PreAuthorize("@discoveryAuthz.sessionPermission(#sessionId, 'SESSION_READ', authentication)")
    public ResponseEntity<TranscriptResponse> getTranscript(UUID sessionId) {
        DiscoverySession session = getTranscript.handle(new GetSessionTranscriptQuery(sessionId));
        TranscriptResponse response = new TranscriptResponse(sessionId, session.getTranscript());
        return ResponseEntity.ok(response);
    }
}
