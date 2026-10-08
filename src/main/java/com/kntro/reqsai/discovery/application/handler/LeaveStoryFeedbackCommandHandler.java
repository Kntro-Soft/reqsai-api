package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.LeaveStoryFeedbackCommand;
import com.kntro.reqsai.discovery.domain.model.StoryFeedback;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** A client approves or comments on a story through a share link. */
@Component
@RequiredArgsConstructor
public class LeaveStoryFeedbackCommandHandler {

    private final SharedLinkGateway gateway;
    private final StoryFeedbackWriter writer;

    public StoryFeedback handle(LeaveStoryFeedbackCommand command) {
        return gateway.withActiveLink(command.token(), link -> writer.write(link, command));
    }
}
