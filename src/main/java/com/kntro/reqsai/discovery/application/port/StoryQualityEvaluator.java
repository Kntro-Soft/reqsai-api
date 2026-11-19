package com.kntro.reqsai.discovery.application.port;

import com.kntro.reqsai.discovery.domain.model.QualityCharacteristic;
import com.kntro.reqsai.discovery.domain.model.QualityFinding;
import com.kntro.reqsai.discovery.domain.model.UserStory;

import java.util.List;

/**
 * Judges the quality of a story against the ISO/IEC/IEEE 29148 and Quality User Story characteristics
 * (US68). The first adapter is rule-based, so it is deterministic, free and independent of AI quota; a
 * different adapter can replace it without touching the use cases.
 */
public interface StoryQualityEvaluator {

    /**
     * Evaluates the story as it is now (fields and acceptance criteria).
     *
     * @return exactly one finding per {@link QualityCharacteristic}
     */
    List<QualityFinding> evaluate(UserStory story);
}
