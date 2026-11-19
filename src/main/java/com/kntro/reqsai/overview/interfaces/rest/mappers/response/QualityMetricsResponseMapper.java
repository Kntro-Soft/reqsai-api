package com.kntro.reqsai.overview.interfaces.rest.mappers.response;

import com.kntro.reqsai.overview.application.result.AcceptanceIndicator;
import com.kntro.reqsai.overview.application.result.ClarificationIndicator;
import com.kntro.reqsai.overview.application.result.PostProcessingIndicator;
import com.kntro.reqsai.overview.application.result.QualityIndicators;
import com.kntro.reqsai.overview.interfaces.rest.dto.response.QualityMetricsResponse;

/** Maps the quality indicators to their REST response. */
public final class QualityMetricsResponseMapper {

    private QualityMetricsResponseMapper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static QualityMetricsResponse toResponse(QualityIndicators indicators) {
        return new QualityMetricsResponse(indicators.from(), indicators.to(), indicators.hasData(),
                indicators.sessions(), toResponse(indicators.acceptance()), toResponse(indicators.postProcessing()),
                toResponse(indicators.clarification()));
    }

    private static QualityMetricsResponse.Acceptance toResponse(AcceptanceIndicator acceptance) {
        return new QualityMetricsResponse.Acceptance(acceptance.similarityThreshold(), acceptance.decided(),
                acceptance.accepted(), acceptance.acceptedAsGenerated(), acceptance.acceptedRewritten(),
                acceptance.rejected(), acceptance.withoutBaseline(), acceptance.rate());
    }

    private static QualityMetricsResponse.PostProcessing toResponse(PostProcessingIndicator postProcessing) {
        return new QualityMetricsResponse.PostProcessing(postProcessing.measured(), postProcessing.unmeasured(),
                postProcessing.averageSeconds(), postProcessing.medianSeconds(),
                postProcessing.sessions().stream().map(review -> new QualityMetricsResponse.SessionReview(
                        review.sessionId(), review.title(), review.purpose(), review.endedAt(), review.seconds()))
                        .toList());
    }

    private static QualityMetricsResponse.Clarification toResponse(ClarificationIndicator clarification) {
        return new QualityMetricsResponse.Clarification(clarification.meetings(), clarification.sessions(),
                clarification.share());
    }
}
