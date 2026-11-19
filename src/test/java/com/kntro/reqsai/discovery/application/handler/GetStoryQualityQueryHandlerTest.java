package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.StoryQualityAssessmentRepository;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.application.query.GetStoryQualityQuery;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryError;
import com.kntro.reqsai.discovery.domain.model.QualityCharacteristic;
import com.kntro.reqsai.discovery.domain.model.QualityFinding;
import com.kntro.reqsai.discovery.domain.model.StoryQualityAssessment;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.discovery.mothers.UserStoryMother;
import com.kntro.reqsai.shared.domain.exception.EntityNotFoundException;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for {@link GetStoryQualityQueryHandler} with mocked ports. */
@Tag("unit")
@DisplayName("Application: Get Story Quality")
@ExtendWith(MockitoExtension.class)
class GetStoryQualityQueryHandlerTest {

    @Mock
    private UserStoryRepository stories;
    @Mock
    private StoryQualityAssessmentRepository assessments;
    @InjectMocks
    private GetStoryQualityQueryHandler handler;

    @Test
    @DisplayName("returns the latest stored assessment of the scoped story")
    void returns_latest_assessment() {
        UUID projectId = UUID.randomUUID();
        UserStory story = UserStoryMother.draft().withProjectId(projectId).build();
        StoryQualityAssessment stored = StoryQualityAssessment.evaluate(story.getId(), List.of(
                QualityFinding.met(QualityCharacteristic.AMBIGUITY, "ok"),
                QualityFinding.met(QualityCharacteristic.ATOMICITY, "ok"),
                QualityFinding.met(QualityCharacteristic.VERIFIABILITY, "ok"),
                QualityFinding.met(QualityCharacteristic.COMPLETENESS, "ok")), Instant.now());
        when(stories.findByIdAndProjectId(story.getId(), projectId)).thenReturn(Optional.of(story));
        when(assessments.findByStoryId(story.getId())).thenReturn(Optional.of(stored));

        assertThat(handler.handle(new GetStoryQualityQuery(projectId, story.getId()))).isSameAs(stored);
    }

    @Test
    @DisplayName("throws 404 STORY_QUALITY_NOT_EVALUATED when the story was never evaluated")
    void throws_not_evaluated() {
        UUID projectId = UUID.randomUUID();
        UserStory story = UserStoryMother.draft().withProjectId(projectId).build();
        when(stories.findByIdAndProjectId(story.getId(), projectId)).thenReturn(Optional.of(story));
        when(assessments.findByStoryId(story.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new GetStoryQualityQuery(projectId, story.getId())))
                .isInstanceOf(EntityNotFoundException.class)
                .satisfies(ex -> assertThat(((EntityNotFoundException) ex).error())
                        .isEqualTo(DiscoveryError.STORY_QUALITY_NOT_EVALUATED));
    }

    @Test
    @DisplayName("throws 404 USER_STORY_NOT_FOUND when the story is not in the project; no assessment is read")
    void throws_story_not_found() {
        UUID projectId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        when(stories.findByIdAndProjectId(storyId, projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new GetStoryQualityQuery(projectId, storyId)))
                .isInstanceOf(EntityNotFoundException.class)
                .satisfies(ex -> assertThat(((EntityNotFoundException) ex).error())
                        .isEqualTo(DiscoveryError.USER_STORY_NOT_FOUND));
        verify(assessments, never()).findByStoryId(storyId);
    }
}
