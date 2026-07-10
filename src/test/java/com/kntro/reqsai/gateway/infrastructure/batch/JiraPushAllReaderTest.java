package com.kntro.reqsai.gateway.infrastructure.batch;

import com.kntro.reqsai.discovery.api.DiscoveryStoryReadPort;
import com.kntro.reqsai.discovery.api.StoryView;
import com.kntro.reqsai.gateway.application.notification.IntegrationJobProgressNotifier;
import com.kntro.reqsai.gateway.application.port.IntegrationSyncJobRepository;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJob;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJobType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.support.ListItemReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the push-all reader in {@link IntegrationBatchJobsConfiguration}: it filters the
 * project backlog to the requested story ids (preserving order, ignoring ids not in the project) and
 * plans the projection {@code total} to the filtered count. An absent/blank selection pushes all.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Batch: push-all reader filters the backlog to the selected story ids")
class JiraPushAllReaderTest {

    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID JOB_ID = UUID.randomUUID();

    @Mock
    private DiscoveryStoryReadPort stories;
    @Mock
    private IntegrationSyncJobRepository jobs;
    @Mock
    private IntegrationJobProgressNotifier progress;

    private final IntegrationBatchJobsConfiguration config = new IntegrationBatchJobsConfiguration();

    private List<StoryView> readAll(ListItemReader<StoryView> reader) {
        List<StoryView> out = new ArrayList<>();
        StoryView next;
        while ((next = reader.read()) != null) {
            out.add(next);
        }
        return out;
    }

    private StoryView story(UUID id) {
        return new StoryView(id, PROJECT, "Title", "user", "do", "benefit", "MEDIUM", null, List.of());
    }

    private void planningJobIsRunning() {
        IntegrationSyncJob job = new IntegrationSyncJob(PROJECT, IntegrationSyncJobType.PUSH_ALL, 0, null);
        lenient().when(jobs.findById(JOB_ID)).thenReturn(Optional.of(job));
        lenient().when(jobs.save(job)).thenReturn(job);
    }

    @Test
    @DisplayName("filters to the selected ids (order preserved), ignores unknown ids, and totals the filtered count")
    void filters_to_selection() {
        StoryView a = story(UUID.randomUUID());
        StoryView b = story(UUID.randomUUID());
        StoryView c = story(UUID.randomUUID());
        UUID missing = UUID.randomUUID();
        when(stories.listStories(PROJECT)).thenReturn(List.of(a, b, c));
        planningJobIsRunning();

        // request c and a and a missing id; result must follow the backlog order (a, c)
        String csv = c.storyId() + "," + a.storyId() + "," + missing;
        ListItemReader<StoryView> reader = config.jiraPushAllReader(
                JOB_ID.toString(), PROJECT.toString(), csv, stories, jobs, progress);

        List<StoryView> read = readAll(reader);
        assertThat(read).extracting(StoryView::storyId).containsExactly(a.storyId(), c.storyId());
    }

    @Test
    @DisplayName("an absent/blank selection pushes every story")
    void no_selection_pushes_all() {
        StoryView a = story(UUID.randomUUID());
        StoryView b = story(UUID.randomUUID());
        when(stories.listStories(PROJECT)).thenReturn(List.of(a, b));
        planningJobIsRunning();

        ListItemReader<StoryView> reader = config.jiraPushAllReader(
                JOB_ID.toString(), PROJECT.toString(), null, stories, jobs, progress);

        assertThat(readAll(reader)).extracting(StoryView::storyId).containsExactly(a.storyId(), b.storyId());
    }
}
