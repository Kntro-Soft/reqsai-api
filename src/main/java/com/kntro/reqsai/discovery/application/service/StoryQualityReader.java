package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.StoryQualityAssessmentRepository;
import com.kntro.reqsai.discovery.application.port.StoryQualityEvaluator;
import com.kntro.reqsai.discovery.domain.model.StoryQualityAssessment;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Reads the quality of many stories at once, for the story lists. A story with a stored assessment gets it;
 * one that was never evaluated is evaluated on the fly and <b>not</b> stored, so listing never writes. The
 * rule-based evaluator is deterministic and free, so the figure matches what evaluating the story would store.
 */
@Component
@RequiredArgsConstructor
public class StoryQualityReader {

    private final StoryQualityEvaluator evaluator;
    private final StoryQualityAssessmentRepository assessments;

    /** The assessment of every given story, by story id. */
    @Transactional(readOnly = true)
    public Map<UUID, StoryQualityAssessment> read(Collection<UserStory> stories) {
        Map<UUID, StoryQualityAssessment> byStory = new HashMap<>();
        assessments.findAllByStoryIdIn(stories.stream().map(UserStory::getId).toList())
                .forEach(assessment -> byStory.put(assessment.getStoryId(), assessment));
        Instant now = Instant.now();
        for (UserStory story : stories) {
            byStory.computeIfAbsent(story.getId(),
                    id -> StoryQualityAssessment.evaluate(id, evaluator.evaluate(story), now));
        }
        return byStory;
    }
}
