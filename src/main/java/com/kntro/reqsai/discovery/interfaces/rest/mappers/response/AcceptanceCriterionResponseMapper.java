package com.kntro.reqsai.discovery.interfaces.rest.mappers.response;

import com.kntro.reqsai.discovery.domain.model.AcceptanceCriterion;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.AcceptanceCriterionResponse;

public final class AcceptanceCriterionResponseMapper {

    private AcceptanceCriterionResponseMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static AcceptanceCriterionResponse toResponse(AcceptanceCriterion criterion) {
        return new AcceptanceCriterionResponse(
                criterion.getId(),
                criterion.getStory().getId(),
                criterion.getScenario(),
                criterion.getGiven(),
                criterion.getWhen(),
                criterion.getThen(),
                criterion.getCreatedAt(),
                criterion.getUpdatedAt());
    }
}
