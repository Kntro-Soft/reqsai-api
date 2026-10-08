package com.kntro.reqsai.discovery.interfaces.rest.controllers;

import com.kntro.reqsai.discovery.application.command.LeaveStoryFeedbackCommand;
import com.kntro.reqsai.discovery.application.handler.GetSharedBacklogQueryHandler;
import com.kntro.reqsai.discovery.application.handler.LeaveStoryFeedbackCommandHandler;
import com.kntro.reqsai.discovery.application.query.GetSharedBacklogQuery;
import com.kntro.reqsai.discovery.interfaces.rest.dto.request.LeaveStoryFeedbackRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.SharedBacklogResponse;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.StoryFeedbackResponse;
import com.kntro.reqsai.discovery.interfaces.rest.mappers.response.SharedBacklogResponseMapper;
import com.kntro.reqsai.discovery.interfaces.rest.swagger.SharedBacklogController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Implementation of the public {@link SharedBacklogController} API contract. */
@RestController
@RequiredArgsConstructor
public class SharedBacklogControllerImpl implements SharedBacklogController {

    private final GetSharedBacklogQueryHandler openLink;
    private final LeaveStoryFeedbackCommandHandler leaveFeedback;

    @Override
    public ResponseEntity<SharedBacklogResponse> open(String token) {
        return ResponseEntity.ok(SharedBacklogResponseMapper.toResponse(
                openLink.handle(new GetSharedBacklogQuery(token))));
    }

    @Override
    public ResponseEntity<StoryFeedbackResponse> leaveFeedback(
            String token, UUID storyId, LeaveStoryFeedbackRequest request) {
        var feedback = leaveFeedback.handle(new LeaveStoryFeedbackCommand(
                token, storyId, request.kind(), request.authorName(), request.comment()));
        return ResponseEntity.status(HttpStatus.CREATED).body(SharedBacklogResponseMapper.toResponse(feedback));
    }
}
