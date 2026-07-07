package com.kntro.reqsai.discovery.interfaces.rest.mappers.response;

import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.SuggestionResponse;

import java.util.List;

public final class SuggestionResponseMapper {

    private SuggestionResponseMapper() {}

    public static SuggestionResponse toResponse(Suggestion s) {
        List<SuggestionResponse.DraftCriterionResponse> criteria = s.getDraftAcceptanceCriteria().stream()
                .map(c -> new SuggestionResponse.DraftCriterionResponse(c.scenario(), c.given(), c.when(), c.then()))
                .toList();
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
                criteria,
                s.getResolvedStoryId(),
                s.getSimilarity(),
                s.getCreatedAt(),
                s.getUpdatedAt());
    }
}
