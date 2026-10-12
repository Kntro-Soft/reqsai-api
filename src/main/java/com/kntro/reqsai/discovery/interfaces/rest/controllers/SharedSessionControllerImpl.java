package com.kntro.reqsai.discovery.interfaces.rest.controllers;

import com.kntro.reqsai.discovery.application.command.AcceptSharedSuggestionCommand;
import com.kntro.reqsai.discovery.application.command.ChangeSharedStoryStatusCommand;
import com.kntro.reqsai.discovery.application.command.DismissSharedSuggestionCommand;
import com.kntro.reqsai.discovery.application.handler.ChangeSharedStoryStatusCommandHandler;
import com.kntro.reqsai.discovery.application.handler.DecideSharedSuggestionCommandHandler;
import com.kntro.reqsai.discovery.application.handler.GetSharedSessionQueryHandler;
import com.kntro.reqsai.discovery.application.query.GetSharedSessionQuery;
import com.kntro.reqsai.discovery.interfaces.rest.dto.request.AcceptSuggestionRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.request.ChangeUserStoryStatusRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.SharedSessionResponse;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.SuggestionResponse;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.UserStoryResponse;
import com.kntro.reqsai.discovery.interfaces.rest.mappers.request.AcceptSuggestionRequestMapper;
import com.kntro.reqsai.discovery.interfaces.rest.mappers.response.SharedSessionResponseMapper;
import com.kntro.reqsai.discovery.interfaces.rest.mappers.response.SuggestionResponseMapper;
import com.kntro.reqsai.discovery.interfaces.rest.mappers.response.UserStoryResponseMapper;
import com.kntro.reqsai.discovery.interfaces.rest.swagger.SharedSessionController;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Implementation of the public {@link SharedSessionController} API contract. */
@RestController
@RequiredArgsConstructor
public class SharedSessionControllerImpl implements SharedSessionController {

    private final GetSharedSessionQueryHandler openLink;
    private final DecideSharedSuggestionCommandHandler decide;
    private final ChangeSharedStoryStatusCommandHandler changeStoryStatus;

    @Override
    public ResponseEntity<SharedSessionResponse> open(String token) {
        return ResponseEntity.ok(SharedSessionResponseMapper.toResponse(openLink.handle(new GetSharedSessionQuery(token))));
    }

    @Override
    public ResponseEntity<SuggestionResponse> accept(String token, UUID suggestionId,
                                                     @Nullable AcceptSuggestionRequest request) {
        var suggestion = decide.handle(new AcceptSharedSuggestionCommand(token, suggestionId,
                AcceptSuggestionRequestMapper.toCommand(null, suggestionId, request)));
        return ResponseEntity.ok(SuggestionResponseMapper.toResponse(suggestion));
    }

    @Override
    public ResponseEntity<SuggestionResponse> dismiss(String token, UUID suggestionId) {
        return ResponseEntity.ok(SuggestionResponseMapper.toResponse(
                decide.handle(new DismissSharedSuggestionCommand(token, suggestionId))));
    }

    @Override
    public ResponseEntity<UserStoryResponse> changeStoryStatus(String token, UUID storyId,
                                                               ChangeUserStoryStatusRequest request) {
        return ResponseEntity.ok(UserStoryResponseMapper.toResponse(
                changeStoryStatus.handle(new ChangeSharedStoryStatusCommand(token, storyId, request.status()))));
    }
}
