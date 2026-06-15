package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.application.query.GetProjectStoryQuery;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryError;
import com.kntro.reqsai.discovery.domain.model.UserStory;
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
import static org.mockito.Mockito.when;

@DisplayName("Application: Get Project Story")
@ExtendWith(MockitoExtension.class)
class GetProjectStoryQueryHandlerTest {

    @Mock
    private UserStoryRepository stories;
    @InjectMocks
    private GetProjectStoryQueryHandler handler;

    @Test
    @DisplayName("should return the story when it belongs to the given project")
    void should_return_story_for_correct_project() {
        UUID projectId = UUID.randomUUID();
        UserStory story = UserStoryMother.draft().withProjectId(projectId).build();
        when(stories.findById(story.getId())).thenReturn(Optional.of(story));

        UserStory result = handler.handle(new GetProjectStoryQuery(projectId, story.getId()));

        assertThat(result.getId()).isEqualTo(story.getId());
        assertThat(result.getProjectId()).isEqualTo(projectId);
    }

    @Test
    @DisplayName("should throw 404 when the story does not exist")
    void should_throw_not_found_when_missing() {
        UUID projectId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        when(stories.findById(storyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new GetProjectStoryQuery(projectId, storyId)))
                .isInstanceOf(EntityNotFoundException.class)
                .satisfies(ex -> assertThat(((EntityNotFoundException) ex).error())
                        .isEqualTo(DiscoveryError.USER_STORY_NOT_FOUND));
    }

    @Test
    @DisplayName("should throw 404 when the story belongs to a different project")
    void should_throw_not_found_when_project_mismatch() {
        UUID ownerProjectId = UUID.randomUUID();
        UUID callerProjectId = UUID.randomUUID();
        UserStory story = UserStoryMother.draft().withProjectId(ownerProjectId).build();
        when(stories.findById(story.getId())).thenReturn(Optional.of(story));

        assertThatThrownBy(() -> handler.handle(new GetProjectStoryQuery(callerProjectId, story.getId())))
                .isInstanceOf(EntityNotFoundException.class)
                .satisfies(ex -> assertThat(((EntityNotFoundException) ex).error())
                        .isEqualTo(DiscoveryError.USER_STORY_NOT_FOUND));
    }
}
