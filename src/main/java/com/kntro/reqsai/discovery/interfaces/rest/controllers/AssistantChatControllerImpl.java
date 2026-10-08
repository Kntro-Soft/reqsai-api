package com.kntro.reqsai.discovery.interfaces.rest.controllers;

import com.kntro.reqsai.discovery.application.command.SendAssistantMessageCommand;
import com.kntro.reqsai.discovery.application.handler.ListAssistantMessagesQueryHandler;
import com.kntro.reqsai.discovery.application.handler.SendAssistantMessageCommandHandler;
import com.kntro.reqsai.discovery.application.query.ListAssistantMessagesQuery;
import com.kntro.reqsai.discovery.interfaces.rest.dto.request.SendAssistantMessageRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.AssistantExchangeResponse;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.AssistantMessageResponse;
import com.kntro.reqsai.discovery.interfaces.rest.mappers.response.AssistantMessageResponseMapper;
import com.kntro.reqsai.discovery.interfaces.rest.swagger.AssistantChatController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Implementation of the {@link AssistantChatController} API contract. */
@RestController
@RequiredArgsConstructor
public class AssistantChatControllerImpl implements AssistantChatController {

    private final SendAssistantMessageCommandHandler sendMessage;
    private final ListAssistantMessagesQueryHandler listMessages;

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'SESSION_RUN', authentication)")
    public ResponseEntity<AssistantExchangeResponse> send(UUID projectId, SendAssistantMessageRequest request) {
        var exchange = sendMessage.handle(new SendAssistantMessageCommand(projectId, request.content()));
        return ResponseEntity.status(HttpStatus.CREATED).body(AssistantMessageResponseMapper.toResponse(exchange));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'SESSION_READ', authentication)")
    public ResponseEntity<List<AssistantMessageResponse>> list(UUID projectId, int limit) {
        return ResponseEntity.ok(listMessages.handle(new ListAssistantMessagesQuery(projectId, limit)).stream()
                .map(AssistantMessageResponseMapper::toResponse)
                .toList());
    }
}
