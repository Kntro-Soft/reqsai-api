package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.ChangeUserStoryStatusCommand;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryError;
import com.kntro.reqsai.discovery.domain.model.StoryStatus;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.discovery.mothers.UserStoryMother;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ChangeUserStoryStatusCommandHandler} with a mocked repository. A review decision
 * only changes the status: the story fields and embedding stay as they were.
 */
@DisplayName("Application: Change User Story Status")
@ExtendWith(MockitoExtension.class)
class ChangeUserStoryStatusCommandHandlerTest {

    @Mock
    private UserStoryRepository stories;
    @InjectMocks
    private ChangeUserStoryStatusCommandHandler handler;

    @Test
    @DisplayName("should approve a draft story and persist it without touching its fields")
    void should_approve_draft_story() {
        UUID projectId = UUID.randomUUID();
        UserStory story = UserStoryMother.draft().withProjectId(projectId).build();
        String title = story.getTitle();
        when(stories.findByIdAndProjectId(story.getId(), projectId)).thenReturn(Optional.of(story));
        when(stories.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UserStory result = handler.handle(new ChangeUserStoryStatusCommand(projectId, story.getId(), StoryStatus.APPROVED));

        assertThat(result.getStatus()).isEqualTo(StoryStatus.APPROVED);
        assertThat(result.getTitle()).isEqualTo(title);
        assertThat(result.getEmbedding()).isNull();
        verify(stories).save(story);
    }

    @Test
    @DisplayName("should reject a transition to MERGED with INVALID_STORY_STATUS and not save")
    void should_reject_invalid_target() {
        UUID projectId = UUID.randomUUID();
        UserStory story = UserStoryMother.draft().withProjectId(projectId).build();
        when(stories.findByIdAndProjectId(story.getId(), projectId)).thenReturn(Optional.of(story));

        assertThatThrownBy(() -> handler.handle(new ChangeUserStoryStatusCommand(projectId, story.getId(), StoryStatus.MERGED)))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).error()).isEqualTo(DiscoveryError.INVALID_STORY_STATUS));
        verify(stories, never()).save(any());
    }

    @Test
    @DisplayName("should throw 404 when the story does not exist in the project")
    void should_throw_not_found_when_missing_in_project() {
        UUID projectId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        when(stories.findByIdAndProjectId(storyId, projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new ChangeUserStoryStatusCommand(projectId, storyId, StoryStatus.APPROVED)))
                .isInstanceOf(EntityNotFoundException.class)
                .satisfies(ex -> assertThat(((EntityNotFoundException) ex).error())
                        .isEqualTo(DiscoveryError.USER_STORY_NOT_FOUND));
        verify(stories, never()).save(any());
    }
}
