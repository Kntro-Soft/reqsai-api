package com.kntro.reqsai.discovery.interfaces.rest.mappers.response;

import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.UserStoryResponse;

/** Maps the {@link UserStory} aggregate to its response DTO. */
public final class UserStoryResponseMapper {

    private UserStoryResponseMapper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static UserStoryResponse toResponse(UserStory story) {
        return new UserStoryResponse(
                story.getId(),
                story.getProjectId(),
                story.getSessionId(),
                story.getTitle(),
                story.getRole(),
                story.getAction(),
                story.getBenefit(),
                story.getPriority().name(),
                story.getStoryPoints(),
                story.getStatus().name(),
                story.getCreatedAt(),
                story.getUpdatedAt());
    }
}
