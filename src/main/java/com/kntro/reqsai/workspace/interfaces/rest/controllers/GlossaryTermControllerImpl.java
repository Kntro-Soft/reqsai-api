package com.kntro.reqsai.workspace.interfaces.rest.controllers;

import com.kntro.reqsai.workspace.application.command.AddGlossaryTermCommand;
import com.kntro.reqsai.workspace.application.handler.AddGlossaryTermCommandHandler;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.AddGlossaryTermRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.GlossaryTermResponse;
import com.kntro.reqsai.workspace.interfaces.rest.mappers.response.GlossaryTermResponseMapper;
import com.kntro.reqsai.workspace.interfaces.rest.swagger.GlossaryTermController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class GlossaryTermControllerImpl implements GlossaryTermController {

    private final AddGlossaryTermCommandHandler addTerm;

    @Override
    public ResponseEntity<GlossaryTermResponse> add(UUID orgId, UUID projectId,
                                                     AddGlossaryTermRequest request,
                                                     Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        var term = addTerm.handle(new AddGlossaryTermCommand(orgId, projectId, request.term(), request.definition(), requestedBy));

        var location = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/api/v1/organizations/{orgId}/projects/{projectId}")
                .buildAndExpand(orgId, projectId)
                .toUri();

        return ResponseEntity.created(location).body(GlossaryTermResponseMapper.toResponse(term));
    }
}
