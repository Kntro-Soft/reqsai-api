package com.kntro.reqsai.workspace.interfaces.rest.controllers;

import com.kntro.reqsai.workspace.application.handler.AddGlossaryTermCommandHandler;
import com.kntro.reqsai.workspace.domain.model.GlossaryTerm;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.AddGlossaryTermRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.GlossaryTermResponse;
import com.kntro.reqsai.workspace.interfaces.rest.mappers.request.GlossaryRequestMapper;
import com.kntro.reqsai.workspace.interfaces.rest.mappers.response.GlossaryResponseMapper;
import com.kntro.reqsai.workspace.interfaces.rest.swagger.GlossaryController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class GlossaryControllerImpl implements GlossaryController {

    private final AddGlossaryTermCommandHandler addGlossaryTerm;

    @Override
    public ResponseEntity<GlossaryTermResponse> addTerm(UUID orgId, UUID projectId, AddGlossaryTermRequest request, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        GlossaryTerm term = addGlossaryTerm.handle(
                GlossaryRequestMapper.toCommand(orgId, projectId, request, requestedBy));

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(term.getId())
                .toUri();

        return ResponseEntity.created(location)
                .body(GlossaryResponseMapper.toResponse(term));
    }
}
