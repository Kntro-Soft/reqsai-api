package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.GenerationResult;
import com.kntro.reqsai.discovery.application.port.SuggestionRepository;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.SuggestionType;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SuggestionCreationService}, focused on how the LLM-returned
 * {@code targetStoryId} (the model now sees the backlog with ids in its prompt) is validated and
 * combined with the embedding-based fallback when classifying suggestions.
 *
 * @see SuggestionCreationService
 */
@DisplayName("Application: SuggestionCreationService")
@ExtendWith(MockitoExtension.class)
class SuggestionCreationServiceTest {

    @Mock private SuggestionRepository suggestions;
    @Mock private UserStoryRepository stories;
    @Mock private EmbeddingPort embeddingPort;

    @InjectMocks
    private SuggestionCreationService service;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    private GenerationResult resultOf(GenerationResult.GeneratedStory story) {
        return new GenerationResult(List.of(story), List.of());
    }

    private GenerationResult.GeneratedStory generated(SuggestionType type, UUID targetStoryId) {
        return new GenerationResult.GeneratedStory(type,
                "Login con 2FA", "usuario", "autenticarme con segundo factor", "más seguridad",
                Priority.HIGH, 3, List.of(), null, targetStoryId);
    }

    @Test
    @DisplayName("should honor a valid LLM targetStoryId for UPDATE_STORY even without an embedding model")
    void should_honor_llm_target_without_embeddings() {
        UserStory target = UserStoryMother.draft().withProjectId(projectId).build();
        when(embeddingPort.isAvailable()).thenReturn(false);
        when(stories.findByIdAndProjectId(target.getId(), projectId)).thenReturn(Optional.of(target));
        when(suggestions.findAllBySessionIdAndStatus(any(), any())).thenReturn(List.of());
        when(suggestions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Suggestion> created = service.createSuggestions(
                resultOf(generated(SuggestionType.UPDATE_STORY, target.getId())), sessionId, projectId);

        assertThat(created).hasSize(1);
        assertThat(created.getFirst().getType()).isEqualTo(SuggestionType.UPDATE_STORY);
        assertThat(created.getFirst().getTargetStoryId()).isEqualTo(target.getId());
    }

    @Test
    @DisplayName("should discard a hallucinated targetStoryId and degrade UPDATE_STORY to NEW_STORY")
    void should_discard_invalid_target() {
        UUID hallucinated = UUID.randomUUID();
        when(embeddingPort.isAvailable()).thenReturn(false);
        when(stories.findByIdAndProjectId(hallucinated, projectId)).thenReturn(Optional.empty());
        when(suggestions.findAllBySessionIdAndStatus(any(), any())).thenReturn(List.of());
        when(suggestions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Suggestion> created = service.createSuggestions(
                resultOf(generated(SuggestionType.UPDATE_STORY, hallucinated)), sessionId, projectId);

        assertThat(created).hasSize(1);
        assertThat(created.getFirst().getType()).isEqualTo(SuggestionType.NEW_STORY);
        assertThat(created.getFirst().getTargetStoryId()).isNull();
    }

    @Test
    @DisplayName("should prefer the LLM target over embedding search for EDGE_CASE")
    void should_prefer_llm_target_for_edge_case() {
        UserStory target = UserStoryMother.draft().withProjectId(projectId).build();
        when(embeddingPort.isAvailable()).thenReturn(true);
        when(embeddingPort.embed(any())).thenReturn(new float[]{0.1f});
        when(stories.findByIdAndProjectId(target.getId(), projectId)).thenReturn(Optional.of(target));
        when(suggestions.findAllBySessionIdAndStatus(any(), any())).thenReturn(List.of());
        when(suggestions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Suggestion> created = service.createSuggestions(
                resultOf(generated(SuggestionType.EDGE_CASE, target.getId())), sessionId, projectId);

        assertThat(created).hasSize(1);
        assertThat(created.getFirst().getType()).isEqualTo(SuggestionType.EDGE_CASE);
        assertThat(created.getFirst().getTargetStoryId()).isEqualTo(target.getId());
    }

    @Test
    @DisplayName("should still upgrade a near-duplicate NEW_STORY to UPDATE_STORY via embedding similarity")
    void should_upgrade_near_duplicate_new_story() {
        UUID existingId = UUID.randomUUID();
        when(embeddingPort.isAvailable()).thenReturn(true);
        when(embeddingPort.embed(any())).thenReturn(new float[]{0.1f});
        when(stories.findMostSimilar(any(), any()))
                .thenReturn(Optional.of(new UserStoryRepository.SimilarStory(existingId, 0.93)));
        when(suggestions.findAllBySessionIdAndStatus(any(), any())).thenReturn(List.of());
        when(suggestions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Suggestion> created = service.createSuggestions(
                resultOf(generated(SuggestionType.NEW_STORY, null)), sessionId, projectId);

        assertThat(created).hasSize(1);
        assertThat(created.getFirst().getType()).isEqualTo(SuggestionType.UPDATE_STORY);
        assertThat(created.getFirst().getTargetStoryId()).isEqualTo(existingId);
    }
}
