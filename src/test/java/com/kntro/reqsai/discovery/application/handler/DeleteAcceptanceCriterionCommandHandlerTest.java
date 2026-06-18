package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.DeleteAcceptanceCriterionCommand;
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

@DisplayName("Application: Delete Acceptance Criterion")
@ExtendWith(MockitoExtension.class)
class DeleteAcceptanceCriterionCommandHandlerTest {

    @Mock
    private UserStoryRepository stories;

    private DeleteAcceptanceCriterionCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DeleteAcceptanceCriterionCommandHandler(stories);
    }

    @Test
    @DisplayName("should remove the acceptance criterion and persist the story")
    void should_delete_and_persist() {
        // Arrange
        UUID projectId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        UserStory story = UserStoryMother.draft().withProjectId(projectId).build();
        AcceptanceCriterion criterion = story.addAcceptanceCriterion("scenario", "given", "when", "then");
        UUID criterionId = criterion.getId();

        when(stories.findByIdAndProjectId(storyId, projectId)).thenReturn(Optional.of(story));
        when(stories.save(any(UserStory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var command = new DeleteAcceptanceCriterionCommand(projectId, storyId, criterionId);

        // Act
        handler.handle(command);

        // Assert
        verify(stories).save(story);
        assertThat(story.getAcceptanceCriteria()).isEmpty();
    }

    @Test
    @DisplayName("should throw error if user story is not found")
    void should_throw_if_story_not_found() {
        // Arrange
        UUID projectId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        UUID criterionId = UUID.randomUUID();

        when(stories.findByIdAndProjectId(storyId, projectId)).thenReturn(Optional.empty());

        var command = new DeleteAcceptanceCriterionCommand(projectId, storyId, criterionId);

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(DomainException.class);

        verify(stories, never()).save(any());
    }
}
