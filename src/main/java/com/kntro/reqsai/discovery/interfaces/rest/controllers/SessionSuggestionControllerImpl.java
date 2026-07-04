package com.kntro.reqsai.discovery.interfaces.rest.controllers;

import com.kntro.reqsai.discovery.application.command.AcceptSuggestionCommand;
import com.kntro.reqsai.discovery.application.command.DismissSuggestionCommand;
import com.kntro.reqsai.discovery.application.handler.AcceptSuggestionCommandHandler;
import com.kntro.reqsai.discovery.application.handler.DismissSuggestionCommandHandler;
import com.kntro.reqsai.discovery.application.handler.ListPendingSuggestionsQueryHandler;
import com.kntro.reqsai.discovery.application.query.ListPendingSuggestionsQuery;
import com.kntro.reqsai.discovery.domain.model.SuggestionStatus;
import com.kntro.reqsai.discovery.interfaces.rest.dto.request.AcceptSuggestionRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.SuggestionResponse;
import com.kntro.reqsai.discovery.interfaces.rest.mappers.response.SuggestionResponseMapper;
import com.kntro.reqsai.discovery.interfaces.rest.swagger.SessionSuggestionController;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class SessionSuggestionControllerImpl implements SessionSuggestionController {

    private final ListPendingSuggestionsQueryHandler listPending;
    private final AcceptSuggestionCommandHandler acceptSuggestion;
    private final DismissSuggestionCommandHandler dismissSuggestion;

    @Override
    @PreAuthorize("@discoveryAuthz.sessionPermission(#sessionId, 'SESSION_READ', authentication)")
    public ResponseEntity<List<SuggestionResponse>> listPending(UUID sessionId, @Nullable SuggestionStatus status) {
        List<SuggestionResponse> body = listPending.handle(new ListPendingSuggestionsQuery(sessionId, status))
                .stream()
                .map(SuggestionResponseMapper::toResponse)
                .toList();
        return ResponseEntity.ok(body);
    }

    @Override
    @PreAuthorize("@discoveryAuthz.sessionPermission(#sessionId, 'SESSION_DECIDE', authentication)")
    public ResponseEntity<SuggestionResponse> accept(UUID sessionId, UUID suggestionId,
                                                     @Nullable AcceptSuggestionRequest request) {
        AcceptSuggestionRequest r = request != null ? request : new AcceptSuggestionRequest(
                null, null, null, null, null, null);
        var suggestion = acceptSuggestion.handle(new AcceptSuggestionCommand(
                sessionId, suggestionId,
                r.editedTitle(), r.editedRole(), r.editedAction(), r.editedBenefit(),
                r.editedPriority(), r.editedStoryPoints()));
        return ResponseEntity.ok(SuggestionResponseMapper.toResponse(suggestion));
    }

    @Override
    @PreAuthorize("@discoveryAuthz.sessionPermission(#sessionId, 'SESSION_DECIDE', authentication)")
    public ResponseEntity<SuggestionResponse> dismiss(UUID sessionId, UUID suggestionId) {
        var suggestion = dismissSuggestion.handle(new DismissSuggestionCommand(sessionId, suggestionId));
        return ResponseEntity.ok(SuggestionResponseMapper.toResponse(suggestion));
    }
}
