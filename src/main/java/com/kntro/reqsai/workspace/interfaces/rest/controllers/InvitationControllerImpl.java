package com.kntro.reqsai.workspace.interfaces.rest.controllers;

import com.kntro.reqsai.workspace.application.command.AcceptInvitationCommand;
import com.kntro.reqsai.workspace.application.handler.AcceptInvitationCommandHandler;
import com.kntro.reqsai.workspace.application.handler.GetInvitationQueryHandler;
import com.kntro.reqsai.workspace.application.query.GetInvitationQuery;
import com.kntro.reqsai.workspace.application.result.AcceptInvitationResult;
import com.kntro.reqsai.workspace.application.result.InvitationDetails;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.AcceptInvitationRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.AcceptInvitationResponse;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.InvitationDetailsResponse;
import com.kntro.reqsai.workspace.interfaces.rest.swagger.InvitationController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class InvitationControllerImpl implements InvitationController {

    private final AcceptInvitationCommandHandler acceptInvitation;
    private final GetInvitationQueryHandler getInvitation;

    @Override
    public ResponseEntity<AcceptInvitationResponse> accept(AcceptInvitationRequest request, Authentication authentication) {
        UUID callerId = UUID.fromString(authentication.getName());
        AcceptInvitationResult result = acceptInvitation.handle(new AcceptInvitationCommand(request.token(), callerId));
        return ResponseEntity.ok(new AcceptInvitationResponse(
                result.organizationId(), result.organizationName(), result.memberId(), result.role()));
    }

    @Override
    public ResponseEntity<InvitationDetailsResponse> getByToken(String token) {
        InvitationDetails details = getInvitation.handle(new GetInvitationQuery(token));
        return ResponseEntity.ok(new InvitationDetailsResponse(
                details.organizationName(), details.role(), details.email(),
                details.invitedByName(), details.status(), details.expired()));
    }
}
