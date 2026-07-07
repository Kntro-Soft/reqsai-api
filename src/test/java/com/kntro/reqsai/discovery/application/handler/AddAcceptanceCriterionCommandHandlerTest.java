package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.AddAcceptanceCriterionCommand;
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

@DisplayName("Application: Add Acceptance Criterion")
@ExtendWith(MockitoExtension.class)
class AddAcceptanceCriterionCommandHandlerTest {

    @Mock
    private UserStoryRepository stories;

    private AddAcceptanceCriterionCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AddAcceptanceCriterionCommandHandler(stories);
    }

    @Test
    @DisplayName("should add the acceptance criterion and persist the story")
    void should_add_and_persist() {
        // Arrange
        UUID projectId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        UserStory story = UserStoryMother.draft().withProjectId(projectId).build();

        when(stories.findByIdAndProjectId(storyId, projectId)).thenReturn(Optional.of(story));
        when(stories.save(any(UserStory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var command = new AddAcceptanceCriterionCommand(
                projectId, storyId, "scenario", "given", "when", "then"
        );

        // Act
        AcceptanceCriterion result = handler.handle(command);

        // Assert
        assertThat(result.getScenario()).isEqualTo("scenario");
        assertThat(result.getGiven()).isEqualTo("given");
        assertThat(result.getWhen()).isEqualTo("when");
        assertThat(result.getThen()).isEqualTo("then");

        verify(stories).save(story);
        assertThat(story.getAcceptanceCriteria()).hasSize(1);
    }

    @Test
    @DisplayName("should throw error if user story is not found")
    void should_throw_if_story_not_found() {
        // Arrange
        UUID projectId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();

        when(stories.findByIdAndProjectId(storyId, projectId)).thenReturn(Optional.empty());

        var command = new AddAcceptanceCriterionCommand(
                projectId, storyId, "scenario", "given", "when", "then"
        );

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(DomainException.class);

        verify(stories, never()).save(any());
    }
}
