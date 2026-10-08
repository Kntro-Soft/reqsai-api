package com.kntro.reqsai.discovery.interfaces.rest.controllers;

import com.kntro.reqsai.discovery.application.command.CreateShareLinkCommand;
import com.kntro.reqsai.discovery.application.command.RevokeShareLinkCommand;
import com.kntro.reqsai.discovery.application.handler.CreateShareLinkCommandHandler;
import com.kntro.reqsai.discovery.application.handler.ListShareLinksQueryHandler;
import com.kntro.reqsai.discovery.application.handler.ListStoryFeedbackQueryHandler;
import com.kntro.reqsai.discovery.application.handler.RevokeShareLinkCommandHandler;
import com.kntro.reqsai.discovery.application.query.ListShareLinksQuery;
import com.kntro.reqsai.discovery.application.query.ListStoryFeedbackQuery;
import com.kntro.reqsai.discovery.interfaces.rest.dto.request.CreateShareLinkRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.ShareLinkResponse;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.StoryFeedbackResponse;
import com.kntro.reqsai.discovery.interfaces.rest.mappers.response.ShareLinkResponseMapper;
import com.kntro.reqsai.discovery.interfaces.rest.mappers.response.SharedBacklogResponseMapper;
import com.kntro.reqsai.discovery.interfaces.rest.swagger.ShareLinkController;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Implementation of the {@link ShareLinkController} API contract. */
@RestController
@RequiredArgsConstructor
public class ShareLinkControllerImpl implements ShareLinkController {

    private final CreateShareLinkCommandHandler createLink;
    private final ListShareLinksQueryHandler listLinks;
    private final RevokeShareLinkCommandHandler revokeLink;
    private final ListStoryFeedbackQueryHandler listFeedback;

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'STORY_WRITE', authentication)")
    public ResponseEntity<ShareLinkResponse> create(UUID projectId, @Nullable CreateShareLinkRequest request) {
        int days = request == null ? CreateShareLinkRequest.DEFAULT_DAYS : request.daysOrDefault();
        var issued = createLink.handle(new CreateShareLinkCommand(projectId, days));
        return ResponseEntity.status(HttpStatus.CREATED).body(ShareLinkResponseMapper.toResponse(issued));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'STORY_READ', authentication)")
    public ResponseEntity<List<ShareLinkResponse>> list(UUID projectId) {
        return ResponseEntity.ok(listLinks.handle(new ListShareLinksQuery(projectId)).stream()
                .map(ShareLinkResponseMapper::toResponse)
                .toList());
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'STORY_WRITE', authentication)")
    public ResponseEntity<ShareLinkResponse> revoke(UUID projectId, UUID linkId) {
        return ResponseEntity.ok(ShareLinkResponseMapper.toResponse(
                revokeLink.handle(new RevokeShareLinkCommand(projectId, linkId))));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#projectId, 'STORY_READ', authentication)")
    public ResponseEntity<List<StoryFeedbackResponse>> feedback(UUID projectId, UUID storyId) {
        return ResponseEntity.ok(listFeedback.handle(new ListStoryFeedbackQuery(projectId, storyId)).stream()
                .map(SharedBacklogResponseMapper::toResponse)
                .toList());
    }
}
