package com.kntro.reqsai.discovery.interfaces.rest.mappers.response;

import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.SuggestionResponse;

public final class SuggestionResponseMapper {

    private SuggestionResponseMapper() {}

    public static SuggestionResponse toResponse(Suggestion s) {
        return new SuggestionResponse(
                s.getId(),
                s.getSessionId(),
                s.getProjectId(),
                s.getType(),
                s.getStatus(),
                s.getDraftTitle(),
                s.getDraftRole(),
                s.getDraftAction(),
                s.getDraftBenefit(),
                s.getDraftPriority() != null ? s.getDraftPriority().name() : null,
                s.getDraftStoryPoints(),
                s.getRelatedTopic(),
                s.getTargetStoryId(),
                s.getQuestion(),
                s.getResolvedStoryId(),
                s.getCreatedAt(),
                s.getUpdatedAt());
    }
}
