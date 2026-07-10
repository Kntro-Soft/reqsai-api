package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.DeleteUserStoryCommand;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryError;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DeleteUserStoryCommandHandler} with a mocked repository. Deleting a story is a
 * hard delete of the aggregate (its acceptance criteria go with it via cascade); a story that is not in
 * the project surfaces as a 404.
 */
@Tag("unit")
@DisplayName("Application: Delete User Story")
@ExtendWith(MockitoExtension.class)
class DeleteUserStoryCommandHandlerTest {

    @Mock
    private UserStoryRepository stories;
    @InjectMocks
    private DeleteUserStoryCommandHandler handler;

    @Test
    @DisplayName("deletes the scoped story (acceptance criteria cascade with the aggregate)")
    void deletes_scoped_story() {
        UUID projectId = UUID.randomUUID();
        UserStory story = UserStoryMother.draft().withProjectId(projectId).build();
        story.addAcceptanceCriterion("scenario", "given", "when", "then");
        when(stories.findByIdAndProjectId(story.getId(), projectId)).thenReturn(Optional.of(story));

        handler.handle(new DeleteUserStoryCommand(projectId, story.getId()));

        // deleting the aggregate cascades to its acceptance criteria (orphanRemoval on the collection)
        assertThat(story.getAcceptanceCriteria()).hasSize(1);
        verify(stories).delete(story);
    }

    @Test
    @DisplayName("throws 404 when the story does not exist in the project; nothing is deleted")
    void throws_not_found_when_missing_in_project() {
        UUID projectId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        when(stories.findByIdAndProjectId(storyId, projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new DeleteUserStoryCommand(projectId, storyId)))
                .isInstanceOf(EntityNotFoundException.class)
                .satisfies(ex -> assertThat(((EntityNotFoundException) ex).error())
                        .isEqualTo(DiscoveryError.USER_STORY_NOT_FOUND));
        verify(stories, never()).delete(any());
    }
}
