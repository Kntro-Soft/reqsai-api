package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.discovery.mothers.UserStoryMother;
import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the lazy re-index pass that gives un-indexed stories (embedding failed at write
 * time) a second chance to enter the vector index. Must be strictly best-effort: never throw.
 *
 * @see UserStoryReindexService
 */
@DisplayName("Application: UserStoryReindexService")
@ExtendWith(MockitoExtension.class)
class UserStoryReindexServiceTest {

    @Mock private UserStoryRepository stories;
    @Mock private EmbeddingPort embeddingPort;

    @InjectMocks
    private UserStoryReindexService service;

    private final UUID projectId = UUID.randomUUID();

    @Test
    @DisplayName("should embed and save every un-indexed story of the batch")
    void should_reindex_pending_stories() {
        UserStory first = UserStoryMother.draft().withProjectId(projectId).build();
        UserStory second = UserStoryMother.draft().withProjectId(projectId).build();
        when(embeddingPort.isAvailable()).thenReturn(true);
        when(stories.findUnindexedByProjectId(projectId, UserStoryReindexService.BATCH_SIZE))
                .thenReturn(List.of(first, second));
        when(embeddingPort.embed(any())).thenReturn(new float[EmbeddingPort.DIMENSIONS]);
        when(stories.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.reindexPending(projectId);

        assertThat(first.isIndexed()).isTrue();
        assertThat(second.isIndexed()).isTrue();
        verify(stories).save(first);
        verify(stories).save(second);
    }

    @Test
    @DisplayName("should abort the pass silently when the provider fails mid-batch")
    void should_abort_silently_on_provider_failure() {
        UserStory first = UserStoryMother.draft().withProjectId(projectId).build();
        UserStory second = UserStoryMother.draft().withProjectId(projectId).build();
        when(embeddingPort.isAvailable()).thenReturn(true);
        when(stories.findUnindexedByProjectId(projectId, UserStoryReindexService.BATCH_SIZE))
                .thenReturn(List.of(first, second));
        when(embeddingPort.embed(any())).thenThrow(new RuntimeException("provider timed out"));

        service.reindexPending(projectId); // must not throw

        assertThat(first.isIndexed()).isFalse();
        assertThat(second.isIndexed()).isFalse();
        verify(stories, never()).save(any());
    }

    @Test
    @DisplayName("should do nothing when no embedding model is configured")
    void should_skip_when_embedding_unavailable() {
        when(embeddingPort.isAvailable()).thenReturn(false);

        service.reindexPending(projectId);

        verify(stories, never()).findUnindexedByProjectId(any(), anyInt());
    }
}
