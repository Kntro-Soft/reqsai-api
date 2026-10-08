package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.StoryFeedbackRepository;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.application.query.ListStoryFeedbackQuery;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.StoryFeedback;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** What clients said about a story through share links, for the team's review. */
@Component
@RequiredArgsConstructor
public class ListStoryFeedbackQueryHandler {

    private final UserStoryRepository stories;
    private final StoryFeedbackRepository feedback;

    @Transactional(readOnly = true)
    public List<StoryFeedback> handle(ListStoryFeedbackQuery query) {
        stories.findByIdAndProjectId(query.storyId(), query.projectId())
                .orElseThrow(() -> DiscoveryExceptions.userStoryNotFound(query.storyId()));
        return feedback.findAllByStoryIds(List.of(query.storyId()));
    }
}
