package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.EvaluateStoryQualityCommand;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.application.service.StoryQualityAssessor;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for {@link EvaluateStoryQualityCommandHandler} with mocked ports. */
@Tag("unit")
@DisplayName("Application: Evaluate Story Quality")
@ExtendWith(MockitoExtension.class)
class EvaluateStoryQualityCommandHandlerTest {

    @Mock
    private UserStoryRepository stories;
    @Mock
    private StoryQualityAssessor assessor;
    @InjectMocks
    private EvaluateStoryQualityCommandHandler handler;

    private static StoryQualityAssessment assessment(UUID storyId) {
        return StoryQualityAssessment.evaluate(storyId, List.of(
                QualityFinding.met(QualityCharacteristic.AMBIGUITY, "ok"),
                QualityFinding.met(QualityCharacteristic.ATOMICITY, "ok"),
                QualityFinding.met(QualityCharacteristic.VERIFIABILITY, "ok"),
                QualityFinding.met(QualityCharacteristic.COMPLETENESS, "ok")), Instant.now());
    }

    @Test
    @DisplayName("evaluates the scoped story and returns the stored assessment")
    void evaluates_scoped_story() {
        UUID projectId = UUID.randomUUID();
        UserStory story = UserStoryMother.draft().withProjectId(projectId).build();
        StoryQualityAssessment expected = assessment(story.getId());
        when(stories.findByIdAndProjectId(story.getId(), projectId)).thenReturn(Optional.of(story));
        when(assessor.evaluate(story)).thenReturn(expected);

        StoryQualityAssessment result = handler.handle(new EvaluateStoryQualityCommand(projectId, story.getId()));

        assertThat(result).isSameAs(expected);
        verify(assessor).evaluate(story);
    }

    @Test
    @DisplayName("throws 404 when the story does not exist in the project; nothing is evaluated")
    void throws_not_found_when_missing_in_project() {
        UUID projectId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        when(stories.findByIdAndProjectId(storyId, projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new EvaluateStoryQualityCommand(projectId, storyId)))
                .isInstanceOf(EntityNotFoundException.class)
                .satisfies(ex -> assertThat(((EntityNotFoundException) ex).error())
                        .isEqualTo(DiscoveryError.USER_STORY_NOT_FOUND));
        verify(assessor, never()).evaluate(any());
    }
}
