package com.kntro.reqsai.discovery.interfaces.rest.mappers.response;

import com.kntro.reqsai.discovery.domain.model.QualityFinding;
import com.kntro.reqsai.discovery.domain.model.StoryQualityAssessment;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.QualityFindingResponse;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.StoryQualityResponse;

public final class StoryQualityResponseMapper {

    private StoryQualityResponseMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static StoryQualityResponse toResponse(StoryQualityAssessment assessment) {
        return new StoryQualityResponse(
                assessment.getStoryId(),
                assessment.getScore(),
                assessment.getFindings().stream().map(StoryQualityResponseMapper::toResponse).toList(),
                assessment.getEvaluatedAt());
    }

    private static QualityFindingResponse toResponse(QualityFinding finding) {
        return new QualityFindingResponse(
                finding.characteristic().name(),
                finding.score(),
                finding.met(),
                finding.observation(),
                finding.suggestion());
    }
}
