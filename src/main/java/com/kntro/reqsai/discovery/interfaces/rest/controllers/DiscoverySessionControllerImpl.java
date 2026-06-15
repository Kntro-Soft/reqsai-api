package com.kntro.reqsai.discovery.interfaces.rest.controllers;

import com.kntro.reqsai.discovery.application.handler.CreateDiscoverySessionCommandHandler;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.interfaces.rest.dto.request.CreateDiscoverySessionRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.DiscoverySessionResponse;
import com.kntro.reqsai.discovery.interfaces.rest.mappers.request.DiscoverySessionRequestMapper;
import com.kntro.reqsai.discovery.interfaces.rest.mappers.response.DiscoverySessionResponseMapper;
import com.kntro.reqsai.discovery.interfaces.rest.swagger.DiscoverySessionController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/** Implementation of the {@link DiscoverySessionController} API contract. */
@RestController
@RequiredArgsConstructor
public class DiscoverySessionControllerImpl implements DiscoverySessionController {

    private final CreateDiscoverySessionCommandHandler createSession;

    @Override
    public ResponseEntity<DiscoverySessionResponse> create(UUID projectId, CreateDiscoverySessionRequest request) {
        DiscoverySession session = createSession.handle(DiscoverySessionRequestMapper.toCommand(projectId, request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(session.getId())
                .toUri();
        return ResponseEntity.created(location).body(DiscoverySessionResponseMapper.toResponse(session));
    }
}
