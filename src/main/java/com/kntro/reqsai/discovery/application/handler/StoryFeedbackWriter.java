package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.LeaveStoryFeedbackCommand;
import com.kntro.reqsai.discovery.application.port.StoryFeedbackRepository;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.ShareLink;
import com.kntro.reqsai.discovery.domain.model.StoryFeedback;
import com.kntro.reqsai.discovery.domain.model.StoryFeedbackKind;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Stores a client's feedback inside the link's tenant (bound by {@link SharedLinkGateway}). */
@Component
@RequiredArgsConstructor
public class StoryFeedbackWriter {

    /** Upper bound on the feedback one link accepts, so an open link cannot be used to flood the tenant. */
    static final long MAX_PER_LINK = 1000;

    private final SharedBacklogReader reader;
    private final StoryFeedbackRepository feedback;

    @Transactional
    public StoryFeedback write(ShareLink link, LeaveStoryFeedbackCommand command) {
        if (!reader.isShared(link, command.storyId())) {
            throw DiscoveryExceptions.userStoryNotFound(command.storyId());
        }
        if (feedback.countByShareLink(link.getId()) >= MAX_PER_LINK) {
            throw DiscoveryExceptions.shareLinkUnavailable();
        }
        StoryFeedback entry = command.kind() == StoryFeedbackKind.APPROVAL
                ? StoryFeedback.approval(command.storyId(), link.getId(), command.authorName(), command.comment())
                : StoryFeedback.comment(command.storyId(), link.getId(), command.authorName(), command.comment());
        return feedback.save(entry);
    }
}
