package com.kntro.reqsai.workspace.interfaces.rest.controllers;

import com.kntro.reqsai.workspace.application.handler.AddGlossaryTermCommandHandler;
import com.kntro.reqsai.workspace.application.handler.DeleteGlossaryTermCommandHandler;
import com.kntro.reqsai.workspace.application.handler.GetGlossaryTermQueryHandler;
import com.kntro.reqsai.workspace.application.handler.ListGlossaryTermsQueryHandler;
import com.kntro.reqsai.workspace.application.handler.UpdateGlossaryTermCommandHandler;
import com.kntro.reqsai.workspace.application.query.GetGlossaryTermQuery;
import com.kntro.reqsai.workspace.application.query.ListGlossaryTermsQuery;
import com.kntro.reqsai.workspace.domain.model.GlossaryTerm;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.AddGlossaryTermRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.UpdateGlossaryTermRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.GlossaryTermResponse;
import com.kntro.reqsai.workspace.interfaces.rest.mappers.request.GlossaryRequestMapper;
import com.kntro.reqsai.workspace.interfaces.rest.mappers.response.GlossaryResponseMapper;
import com.kntro.reqsai.workspace.interfaces.rest.swagger.GlossaryController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class GlossaryControllerImpl implements GlossaryController {

    private final ListGlossaryTermsQueryHandler listGlossaryTerms;
    private final AddGlossaryTermCommandHandler addGlossaryTerm;
    private final GetGlossaryTermQueryHandler getGlossaryTerm;
    private final UpdateGlossaryTermCommandHandler updateGlossaryTerm;
    private final DeleteGlossaryTermCommandHandler deleteGlossaryTerm;

    @Override
    @PreAuthorize("@authz.projectPermission(#orgId, #projectId, 'GLOSSARY_READ', authentication)")
    public ResponseEntity<List<GlossaryTermResponse>> listTerms(UUID orgId, UUID projectId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        List<GlossaryTermResponse> response = listGlossaryTerms.handle(
                        new ListGlossaryTermsQuery(orgId, projectId, requestedBy))
                .stream()
                .map(GlossaryResponseMapper::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#orgId, #projectId, 'GLOSSARY_TERM_WRITE', authentication)")
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

    @Override
    @PreAuthorize("@authz.projectPermission(#orgId, #projectId, 'GLOSSARY_READ', authentication)")
    public ResponseEntity<GlossaryTermResponse> getTerm(UUID orgId, UUID projectId, UUID termId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        GlossaryTerm term = getGlossaryTerm.handle(new GetGlossaryTermQuery(orgId, projectId, termId, requestedBy));
        return ResponseEntity.ok(GlossaryResponseMapper.toResponse(term));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#orgId, #projectId, 'GLOSSARY_TERM_WRITE', authentication)")
    public ResponseEntity<GlossaryTermResponse> updateTerm(UUID orgId, UUID projectId, UUID termId, UpdateGlossaryTermRequest request, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        GlossaryTerm term = updateGlossaryTerm.handle(
                GlossaryRequestMapper.toCommand(orgId, projectId, termId, request, requestedBy));
        return ResponseEntity.ok(GlossaryResponseMapper.toResponse(term));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#orgId, #projectId, 'GLOSSARY_TERM_DELETE', authentication)")
    public ResponseEntity<Void> deleteTerm(UUID orgId, UUID projectId, UUID termId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        deleteGlossaryTerm.handle(GlossaryRequestMapper.toDeleteCommand(orgId, projectId, termId, requestedBy));
        return ResponseEntity.noContent().build();
    }
}
