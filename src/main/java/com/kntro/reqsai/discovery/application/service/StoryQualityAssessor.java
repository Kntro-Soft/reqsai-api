package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.StoryQualityAssessmentRepository;
import com.kntro.reqsai.discovery.application.port.StoryQualityEvaluator;
import com.kntro.reqsai.discovery.domain.model.QualityFinding;
import com.kntro.reqsai.discovery.domain.model.StoryQualityAssessment;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Evaluates a story and keeps its latest {@link StoryQualityAssessment}. The explicit request evaluates
 * the story whether or not it was assessed before; an edit only refreshes a story that already has an
 * assessment, so editing never starts evaluating stories nobody asked about.
 * <p>
 * Runs inside the caller's transaction.
 */
@Component
@RequiredArgsConstructor
public class StoryQualityAssessor {

    private final StoryQualityEvaluator evaluator;
    private final StoryQualityAssessmentRepository assessments;

    /** Evaluates the story and stores the result, creating or replacing its assessment. */
    public StoryQualityAssessment evaluate(UserStory story) {
        List<QualityFinding> findings = evaluator.evaluate(story);
        Instant now = Instant.now();
        StoryQualityAssessment assessment = assessments.findByStoryId(story.getId())
                .map(existing -> {
                    existing.reevaluate(findings, now);
                    return existing;
                })
                .orElseGet(() -> StoryQualityAssessment.evaluate(story.getId(), findings, now));
        return assessments.save(assessment);
    }

    /** After an edit: re-evaluates the story only when it already had an assessment. */
    public void refreshIfAssessed(UserStory story) {
        assessments.findByStoryId(story.getId()).ifPresent(existing -> {
            existing.reevaluate(evaluator.evaluate(story), Instant.now());
            assessments.save(existing);
        });
    }
}
