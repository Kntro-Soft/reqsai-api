package com.kntro.reqsai.discovery.interfaces.rest.mappers.response;

import com.kntro.reqsai.discovery.application.handler.SharedBacklog;
import com.kntro.reqsai.discovery.domain.model.StoryFeedback;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.SharedBacklogResponse;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.SharedStoryResponse;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.StoryFeedbackResponse;

/** Maps what a share link shows, and client feedback, to their response DTOs. */
public final class SharedBacklogResponseMapper {

    private SharedBacklogResponseMapper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static SharedBacklogResponse toResponse(SharedBacklog backlog) {
        return new SharedBacklogResponse(
                backlog.projectName(),
                backlog.expiresAt(),
                backlog.stories().stream().map(SharedBacklogResponseMapper::toResponse).toList());
    }

    public static StoryFeedbackResponse toResponse(StoryFeedback feedback) {
        return new StoryFeedbackResponse(
                feedback.getId(),
                feedback.getStoryId(),
                feedback.getKind().name(),
                feedback.getAuthorName(),
                feedback.getComment(),
                feedback.getCreatedAt());
    }

    private static SharedStoryResponse toResponse(SharedBacklog.SharedStory shared) {
        UserStory story = shared.story();
        return new SharedStoryResponse(
                story.getId(),
                story.getTitle(),
                story.getRole(),
                story.getAction(),
                story.getBenefit(),
                story.getPriority().name(),
                story.getStoryPoints(),
                story.getStatus().name(),
                story.getAcceptanceCriteria().stream().map(AcceptanceCriterionResponseMapper::toResponse).toList(),
                shared.feedback().stream().map(SharedBacklogResponseMapper::toResponse).toList());
    }
}
