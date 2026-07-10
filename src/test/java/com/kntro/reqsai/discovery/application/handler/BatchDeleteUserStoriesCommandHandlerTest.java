package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.BatchDeleteUserStoriesCommand;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.discovery.mothers.UserStoryMother;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BatchDeleteUserStoriesCommandHandler}: it deletes only the candidate ids that
 * belong to the project, silently skips the rest, and returns the number actually deleted.
 */
@Tag("unit")
@DisplayName("Application: Batch Delete User Stories")
@ExtendWith(MockitoExtension.class)
class BatchDeleteUserStoriesCommandHandlerTest {

    @Mock
    private UserStoryRepository stories;
    @InjectMocks
    private BatchDeleteUserStoriesCommandHandler handler;

    @Test
    @DisplayName("deletes only the stories found in the project and returns the deleted count")
    void deletes_found_and_skips_missing() {
        UUID projectId = UUID.randomUUID();
        UserStory a = UserStoryMother.draft().withProjectId(projectId).build();
        UserStory b = UserStoryMother.draft().withProjectId(projectId).build();
        UUID missing = UUID.randomUUID();
        List<UUID> requested = List.of(a.getId(), b.getId(), missing);

        // the repository only returns the two ids that belong to the project; the missing id is skipped
        when(stories.findAllByProjectIdAndIdIn(projectId, requested)).thenReturn(List.of(a, b));

        int deleted = handler.handle(new BatchDeleteUserStoriesCommand(projectId, requested));

        assertThat(deleted).isEqualTo(2);
        verify(stories).delete(a);
        verify(stories).delete(b);
        verify(stories, times(2)).delete(any());
    }

    @Test
    @DisplayName("returns zero and deletes nothing when none of the ids are in the project")
    void deletes_nothing_when_all_missing() {
        UUID projectId = UUID.randomUUID();
        List<UUID> requested = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(stories.findAllByProjectIdAndIdIn(projectId, requested)).thenReturn(List.of());

        int deleted = handler.handle(new BatchDeleteUserStoriesCommand(projectId, requested));

        assertThat(deleted).isZero();
        verify(stories, never()).delete(any());
    }
}
