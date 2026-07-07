package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.UpdateUserStoryCommand;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryError;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.discovery.mothers.UpdateUserStoryCommandMother;
import com.kntro.reqsai.discovery.mothers.UserStoryMother;
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
 * Unit tests for {@link UpdateUserStoryCommandHandler} with a mocked repository. A manual edit is a
 * straight field update — it must NOT touch the embedding or run deduplication.
 */
@DisplayName("Application: Update User Story")
@ExtendWith(MockitoExtension.class)
class UpdateUserStoryCommandHandlerTest {

    @Mock
    private UserStoryRepository stories;
    @InjectMocks
    private UpdateUserStoryCommandHandler handler;

    @Test
    @DisplayName("should update the core fields and persist without recomputing the embedding")
    void should_update_fields_without_reembedding() {
        UUID projectId = UUID.randomUUID();
        UserStory story = UserStoryMother.draft().withProjectId(projectId).build();
        when(stories.findByIdAndProjectId(story.getId(), projectId)).thenReturn(Optional.of(story));
        when(stories.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateUserStoryCommand command = new UpdateUserStoryCommand(
                projectId, story.getId(), "New title", "new role",
                "do the new thing", "gain the new benefit", Priority.CRITICAL, 8);

        UserStory result = handler.handle(command);

        assertThat(result.getTitle()).isEqualTo("New title");
        assertThat(result.getRole()).isEqualTo("new role");
        assertThat(result.getAction()).isEqualTo("do the new thing");
        assertThat(result.getBenefit()).isEqualTo("gain the new benefit");
        assertThat(result.getPriority()).isEqualTo(Priority.CRITICAL);
        assertThat(result.getStoryPoints()).isEqualTo(8);
        // draft mother creates the story without an embedding; a manual edit must not add one
        assertThat(result.getEmbedding()).isNull();
        verify(stories).save(story);
    }

    @Test
    @DisplayName("should throw 404 when the story does not exist in the project")
    void should_throw_not_found_when_missing_in_project() {
        UUID projectId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        when(stories.findByIdAndProjectId(storyId, projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(UpdateUserStoryCommandMother.forStoryInProject(projectId, storyId)))
                .isInstanceOf(EntityNotFoundException.class)
                .satisfies(ex -> assertThat(((EntityNotFoundException) ex).error())
                        .isEqualTo(DiscoveryError.USER_STORY_NOT_FOUND));
        verify(stories, never()).save(any());
    }
}
