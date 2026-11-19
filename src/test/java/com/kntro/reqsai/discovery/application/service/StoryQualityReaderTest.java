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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for {@link StoryQualityReader} with mocked ports. */
@Tag("unit")
@DisplayName("Application: Story Quality Reader")
@ExtendWith(MockitoExtension.class)
class StoryQualityReaderTest {

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
    private StoryQualityReader reader;

    @Test
    @DisplayName("a story with a stored assessment gets it, and is not evaluated again")
    void uses_the_stored_assessment() {
        UserStory story = UserStoryMother.draft().build();
        StoryQualityAssessment stored = StoryQualityAssessment.evaluate(story.getId(), ALL_UNMET,
                Instant.parse("2026-01-01T00:00:00Z"));
        when(assessments.findAllByStoryIdIn(List.of(story.getId()))).thenReturn(List.of(stored));

        Map<UUID, StoryQualityAssessment> result = reader.read(List.of(story));

        assertThat(result.get(story.getId())).isSameAs(stored);
        verify(evaluator, never()).evaluate(story);
    }

    @Test
    @DisplayName("a story never evaluated is evaluated on the fly and nothing is stored")
    void evaluates_without_storing() {
        UserStory story = UserStoryMother.draft().build();
        when(assessments.findAllByStoryIdIn(List.of(story.getId()))).thenReturn(List.of());
        when(evaluator.evaluate(story)).thenReturn(ALL_MET);

        Map<UUID, StoryQualityAssessment> result = reader.read(List.of(story));

        assertThat(result.get(story.getId()).getScore()).isEqualTo(100);
        verify(assessments, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
