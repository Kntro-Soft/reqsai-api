package com.kntro.reqsai.gateway.application.handler;

import com.kntro.reqsai.discovery.api.DiscoveryStoryReadPort;
import com.kntro.reqsai.discovery.api.StoryView;
import com.kntro.reqsai.gateway.application.command.PushAllStoriesCommand;
import com.kntro.reqsai.gateway.application.port.IntegrationJobLauncher;
import com.kntro.reqsai.gateway.application.port.ProjectIntegrationTargetRepository;
import com.kntro.reqsai.gateway.application.service.IntegrationSyncJobStarter;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJob;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJobStatus;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJobType;
import com.kntro.reqsai.gateway.domain.model.ProjectIntegrationTarget;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Application: Push all stories command handler (async job)")
class PushAllStoriesCommandHandlerTest {

    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    @Mock
    private ProjectIntegrationTargetRepository targets;
    @Mock
    private DiscoveryStoryReadPort stories;
    @Mock
    private IntegrationSyncJobStarter starter;
    @Mock
    private IntegrationJobLauncher launcher;
    @InjectMocks
    private PushAllStoriesCommandHandler handler;

    @Test
    @DisplayName("creates a RUNNING job with the story count as total and dispatches the async push")
    void starts_job_and_launches() {
        when(targets.findByProjectId(PROJECT)).thenReturn(Optional.of(mock(ProjectIntegrationTarget.class)));
        when(stories.listStories(PROJECT)).thenReturn(List.of(story(), story(), story()));
        IntegrationSyncJob job = new IntegrationSyncJob(PROJECT, IntegrationSyncJobType.PUSH_ALL, 3, USER);
        when(starter.start(PROJECT, IntegrationSyncJobType.PUSH_ALL, 3, USER)).thenReturn(job);

        IntegrationSyncJob result = handler.handle(new PushAllStoriesCommand(PROJECT, null, USER));

        assertThat(result.getStatus()).isEqualTo(IntegrationSyncJobStatus.RUNNING);
        assertThat(result.getTotal()).isEqualTo(3);
        assertThat(result.getProcessed()).isZero();
        verify(launcher).launchPushAll(job.getId(), PROJECT, null);
    }

    @Test
    @DisplayName("with a story-id selection, total counts only selected stories present in the project")
    void starts_job_with_selection() {
        StoryView a = story();
        StoryView b = story();
        StoryView c = story();
        UUID missing = UUID.randomUUID();
        List<UUID> selection = List.of(a.storyId(), c.storyId(), missing);

        when(targets.findByProjectId(PROJECT)).thenReturn(Optional.of(mock(ProjectIntegrationTarget.class)));
        when(stories.listStories(PROJECT)).thenReturn(List.of(a, b, c));
        IntegrationSyncJob job = new IntegrationSyncJob(PROJECT, IntegrationSyncJobType.PUSH_ALL, 2, USER);
        when(starter.start(PROJECT, IntegrationSyncJobType.PUSH_ALL, 2, USER)).thenReturn(job);

        IntegrationSyncJob result = handler.handle(new PushAllStoriesCommand(PROJECT, selection, USER));

        // only a and c exist in the project; the missing id is ignored
        assertThat(result.getTotal()).isEqualTo(2);
        verify(launcher).launchPushAll(job.getId(), PROJECT, selection);
    }

    @Test
    @DisplayName("409 INTEGRATION_TARGET_NOT_CONFIGURED when no target exists; nothing is launched")
    void no_target_conflicts() {
        when(targets.findByProjectId(PROJECT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new PushAllStoriesCommand(PROJECT, null, USER)))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).error().code())
                        .isEqualTo("INTEGRATION_TARGET_NOT_CONFIGURED"));
        verify(launcher, never()).launchPushAll(any(), any(), any());
    }

    private static StoryView story() {
        return new StoryView(UUID.randomUUID(), PROJECT, "Title", "user", "do", "benefit", "MEDIUM", null, List.of());
    }
}
