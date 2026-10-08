package com.kntro.reqsai.discovery.interfaces.rest.controllers;

import com.kntro.reqsai.discovery.application.handler.AcceptSuggestionCommandHandler;
import com.kntro.reqsai.discovery.application.handler.DismissSuggestionCommandHandler;
import com.kntro.reqsai.discovery.application.handler.ListProjectSuggestionsQueryHandler;
import com.kntro.reqsai.discovery.application.query.ListProjectSuggestionsQuery;
import com.kntro.reqsai.discovery.domain.model.SuggestionStatus;
import com.kntro.reqsai.discovery.interfaces.rest.dto.request.AcceptSuggestionRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.SuggestionResponse;
import com.kntro.reqsai.discovery.interfaces.rest.mappers.request.AcceptSuggestionRequestMapper;
import com.kntro.reqsai.discovery.interfaces.rest.mappers.response.SuggestionResponseMapper;
import com.kntro.reqsai.discovery.interfaces.rest.swagger.ProjectSuggestionController;
import com.kntro.reqsai.shared.interfaces.pagination.PageCriteria;
import com.kntro.reqsai.shared.interfaces.pagination.PageResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Implementation of the {@link ProjectSuggestionController} API contract. */
@RestController
@RequiredArgsConstructor
public class ProjectSuggestionControllerImpl implements ProjectSuggestionController {

    private final ListProjectSuggestionsQueryHandler listProjectSuggestions;
    private final AcceptSuggestionCommandHandler acceptSuggestion;
    private final DismissSuggestionCommandHandler dismissSuggestion;

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'SESSION_READ', authentication)")
    public ResponseEntity<PageResponse<SuggestionResponse>> list(
            UUID projectId, SuggestionStatus status, Integer page, Integer size, String sortBy, String sortDirection) {
        PageResponse<SuggestionResponse> response = PageResponse.of(
                listProjectSuggestions.handle(new ListProjectSuggestionsQuery(
                                projectId, status, PageCriteria.of(page, size, sortBy, sortDirection)))
                        .map(SuggestionResponseMapper::toResponse));
        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'SESSION_DECIDE', authentication)")
    public ResponseEntity<SuggestionResponse> accept(UUID projectId, UUID suggestionId,
                                                     @Nullable AcceptSuggestionRequest request) {
        var suggestion = acceptSuggestion.handleInProject(projectId,
                AcceptSuggestionRequestMapper.toCommand(null, suggestionId, request));
        return ResponseEntity.ok(SuggestionResponseMapper.toResponse(suggestion));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'SESSION_DECIDE', authentication)")
    public ResponseEntity<SuggestionResponse> dismiss(UUID projectId, UUID suggestionId) {
        var suggestion = dismissSuggestion.handleInProject(projectId, suggestionId);
        return ResponseEntity.ok(SuggestionResponseMapper.toResponse(suggestion));
    }
}
