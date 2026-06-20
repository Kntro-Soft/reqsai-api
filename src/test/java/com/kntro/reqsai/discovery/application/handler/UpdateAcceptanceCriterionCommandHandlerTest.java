package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.UpdateAcceptanceCriterionCommand;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.model.AcceptanceCriterion;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.discovery.mothers.UserStoryMother;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Application: Update Acceptance Criterion")
@ExtendWith(MockitoExtension.class)
class UpdateAcceptanceCriterionCommandHandlerTest {

    @Mock
    private UserStoryRepository stories;

    private UpdateAcceptanceCriterionCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UpdateAcceptanceCriterionCommandHandler(stories);
    }

    @Test
    @DisplayName("should update the acceptance criterion and persist the story")
    void should_update_and_persist() {
        // Arrange
        UUID projectId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        UserStory story = UserStoryMother.draft().withProjectId(projectId).build();
        AcceptanceCriterion criterion = story.addAcceptanceCriterion("old scenario", "old given", "old when", "old then");
        UUID criterionId = criterion.getId();

        when(stories.findByIdAndProjectId(storyId, projectId)).thenReturn(Optional.of(story));
        when(stories.save(any(UserStory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var command = new UpdateAcceptanceCriterionCommand(
                projectId, storyId, criterionId, "updated scenario", "updated given", "updated when", "updated then"
        );

        // Act
        AcceptanceCriterion result = handler.handle(command);

        // Assert
        assertThat(result).isEqualTo(criterion);
        assertThat(result.getScenario()).isEqualTo("updated scenario");
        assertThat(result.getGiven()).isEqualTo("updated given");
        assertThat(result.getWhen()).isEqualTo("updated when");
        assertThat(result.getThen()).isEqualTo("updated then");

        verify(stories).save(story);
    }

    @Test
    @DisplayName("should throw error if user story is not found")
    void should_throw_if_story_not_found() {
        // Arrange
        UUID projectId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        UUID criterionId = UUID.randomUUID();

        when(stories.findByIdAndProjectId(storyId, projectId)).thenReturn(Optional.empty());

        var command = new UpdateAcceptanceCriterionCommand(
                projectId, storyId, criterionId, "scenario", "given", "when", "then"
        );

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(DomainException.class);

        verify(stories, never()).save(any());
    }
}
