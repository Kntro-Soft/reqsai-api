package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.StoryQualityAssessmentRepository;
import com.kntro.reqsai.discovery.application.port.StoryQualityEvaluator;
import com.kntro.reqsai.discovery.domain.model.QualityCharacteristic;
import com.kntro.reqsai.discovery.domain.model.QualityFinding;
import com.kntro.reqsai.discovery.domain.model.StoryQualityAssessment;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.discovery.mothers.UserStoryMother;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for {@link StoryQualityAssessor} with mocked ports. */
@Tag("unit")
@DisplayName("Application: Story Quality Assessor")
@ExtendWith(MockitoExtension.class)
class StoryQualityAssessorTest {

    private static final List<QualityFinding> ALL_MET = List.of(
            QualityFinding.met(QualityCharacteristic.AMBIGUITY, "ok"),
            QualityFinding.met(QualityCharacteristic.ATOMICITY, "ok"),
            QualityFinding.met(QualityCharacteristic.VERIFIABILITY, "ok"),
            QualityFinding.met(QualityCharacteristic.COMPLETENESS, "ok"));
    private static final List<QualityFinding> ALL_UNMET = List.of(
            QualityFinding.unmet(QualityCharacteristic.AMBIGUITY, 0, "mal", "arregla"),
            QualityFinding.unmet(QualityCharacteristic.ATOMICITY, 0, "mal", "arregla"),
            QualityFinding.unmet(QualityCharacteristic.VERIFIABILITY, 0, "mal", "arregla"),
            QualityFinding.unmet(QualityCharacteristic.COMPLETENESS, 0, "mal", "arregla"));

    @Mock
    private StoryQualityEvaluator evaluator;
    @Mock
    private StoryQualityAssessmentRepository assessments;
    @InjectMocks
    private StoryQualityAssessor assessor;

    @Test
    @DisplayName("evaluate creates the first assessment of a story with the evaluator's findings")
    void creates_first_assessment() {
        UserStory story = UserStoryMother.draft().build();
        when(evaluator.evaluate(story)).thenReturn(ALL_MET);
        when(assessments.findByStoryId(story.getId())).thenReturn(Optional.empty());
        when(assessments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StoryQualityAssessment result = assessor.evaluate(story);

        assertThat(result.getStoryId()).isEqualTo(story.getId());
        assertThat(result.getScore()).isEqualTo(100);
        assertThat(result.getFindings()).isEqualTo(ALL_MET);
        verify(assessments).save(result);
    }

    @Test
    @DisplayName("evaluate again replaces the existing assessment instead of adding another")
    void replaces_existing_assessment() {
        UserStory story = UserStoryMother.draft().build();
        StoryQualityAssessment existing = StoryQualityAssessment.evaluate(story.getId(), ALL_UNMET,
                Instant.parse("2026-01-01T00:00:00Z"));
        when(evaluator.evaluate(story)).thenReturn(ALL_MET);
        when(assessments.findByStoryId(story.getId())).thenReturn(Optional.of(existing));
        when(assessments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StoryQualityAssessment result = assessor.evaluate(story);

        assertThat(result).isSameAs(existing);
        assertThat(result.getScore()).isEqualTo(100);
        assertThat(result.getEvaluatedAt()).isAfter(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    @DisplayName("refreshIfAssessed re-evaluates a story that already has an assessment")
    void refreshes_assessed_story() {
        UserStory story = UserStoryMother.draft().build();
        StoryQualityAssessment existing = StoryQualityAssessment.evaluate(story.getId(), ALL_UNMET,
                Instant.parse("2026-01-01T00:00:00Z"));
        when(assessments.findByStoryId(story.getId())).thenReturn(Optional.of(existing));
        when(evaluator.evaluate(story)).thenReturn(ALL_MET);

        assessor.refreshIfAssessed(story);

        assertThat(existing.getScore()).isEqualTo(100);
        verify(assessments).save(existing);
    }

    @Test
    @DisplayName("refreshIfAssessed leaves a never-evaluated story alone")
    void ignores_unassessed_story() {
        UserStory story = UserStoryMother.draft().build();
        when(assessments.findByStoryId(story.getId())).thenReturn(Optional.empty());

        assessor.refreshIfAssessed(story);

        verify(evaluator, never()).evaluate(any());
        verify(assessments, never()).save(any());
    }
}
