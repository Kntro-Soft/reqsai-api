package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.api.ExternalIssueInput;
import com.kntro.reqsai.discovery.api.ImportedStory;
import com.kntro.reqsai.discovery.api.StoryDuplicateCheck;
import com.kntro.reqsai.discovery.application.handler.CreateUserStoryCommandHandler;
import com.kntro.reqsai.discovery.application.port.GenerationResult;
import com.kntro.reqsai.discovery.application.port.GenerationResult.GeneratedCriterion;
import com.kntro.reqsai.discovery.application.port.GenerationResult.GeneratedStory;
import com.kntro.reqsai.discovery.application.port.RequirementGenerationPort;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Application: Discovery story write port (external issue import)")
class DiscoveryStoryWritePortImplTest {

    private static final UUID PROJECT = UUID.randomUUID();

    @Mock
    private RequirementGenerationPort generationPort;
    @Mock
    private UserStoryRepository stories;
    @Mock
    private EmbeddingPort embeddingPort;

    private DiscoveryStoryWritePortImpl port;

    @BeforeEach
    void setUp() {
        UserStoryDeduplicationService dedup = new UserStoryDeduplicationService(stories, embeddingPort);
        CreateUserStoryCommandHandler createHandler = new CreateUserStoryCommandHandler(stories, dedup);
        port = new DiscoveryStoryWritePortImpl(generationPort, createHandler, stories, embeddingPort);
    }

    @Test
    @DisplayName("LLM path: uses the generated role/action/benefit + criteria and creates the story")
    void llm_path_creates_structured_story() {
        when(generationPort.isAvailable()).thenReturn(true);
        when(generationPort.generate(any(), any())).thenReturn(new GenerationResult(List.of(
                new GeneratedStory("Login con Google", "usuario registrado",
                        "iniciar sesión con Google", "no recordar otra contraseña",
                        Priority.HIGH, 3,
                        List.of(new GeneratedCriterion("ok", "en login", "click Google", "redirige a OAuth"))))));
        when(stories.save(any())).thenAnswer(i -> i.getArgument(0));
        // embeddingPort.isAvailable() defaults to false -> dedup skipped

        ImportedStory result = port.importFromExternalIssue(new ExternalIssueInput(
                PROJECT, "Login con Google", "Como usuario quiero entrar con Google", "es-PE"));

        assertThat(result.status()).isEqualTo(ImportedStory.Status.CREATED);
        ArgumentCaptor<UserStory> saved = ArgumentCaptor.forClass(UserStory.class);
        verify(stories, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        UserStory story = saved.getValue();
        assertThat(story.getTitle()).isEqualTo("Login con Google");
        assertThat(story.getRole()).isEqualTo("usuario registrado");
        assertThat(story.getAction()).isEqualTo("iniciar sesión con Google");
        assertThat(story.getAcceptanceCriteria()).hasSize(1);
    }

    @Test
    @DisplayName("fallback path: no LLM configured -> safe deterministic mapping still satisfies validation")
    void fallback_path_when_llm_unavailable() {
        when(generationPort.isAvailable()).thenReturn(false);
        when(stories.save(any())).thenAnswer(i -> i.getArgument(0));

        ImportedStory result = port.importFromExternalIssue(new ExternalIssueInput(
                PROJECT, "Bulk CSV import", "Upload a CSV to seed the backlog", null));

        assertThat(result.status()).isEqualTo(ImportedStory.Status.CREATED);
        ArgumentCaptor<UserStory> saved = ArgumentCaptor.forClass(UserStory.class);
        verify(stories).save(saved.capture());
        UserStory story = saved.getValue();
        assertThat(story.getTitle()).isEqualTo("Bulk CSV import");
        assertThat(story.getRole()).isNotBlank();
        assertThat(story.getAction()).isNotBlank();
        assertThat(story.getBenefit()).contains("Upload a CSV");
    }

    @Test
    @DisplayName("fallback path: generation failure falls back rather than aborting the import")
    void fallback_when_generation_throws() {
        when(generationPort.isAvailable()).thenReturn(true);
        when(generationPort.generate(any(), any())).thenThrow(new RuntimeException("model timeout"));
        when(stories.save(any())).thenAnswer(i -> i.getArgument(0));

        ImportedStory result = port.importFromExternalIssue(new ExternalIssueInput(
                PROJECT, "Password reset", "As a user I want to reset my password", "en-US"));

        assertThat(result.status()).isEqualTo(ImportedStory.Status.CREATED);
        verify(stories).save(any());
    }

    @Test
    @DisplayName("duplicate: create hits the dedup gate -> reported as DUPLICATE with the existing story id")
    void duplicate_is_reported_not_created() {
        UUID existing = UUID.randomUUID();
        when(generationPort.isAvailable()).thenReturn(false);
        when(embeddingPort.isAvailable()).thenReturn(true);
        when(embeddingPort.embed(any())).thenReturn(new float[EmbeddingPort.DIMENSIONS]);
        when(stories.findMostSimilar(any(), any()))
                .thenReturn(Optional.of(new UserStoryRepository.SimilarStory(existing, 0.93)));

        ImportedStory result = port.importFromExternalIssue(new ExternalIssueInput(
                PROJECT, "Duplicate story", "same as an existing one", null));

        assertThat(result.status()).isEqualTo(ImportedStory.Status.DUPLICATE);
        assertThat(result.existingStoryId()).isEqualTo(existing);
        assertThat(result.similarity()).isEqualTo(0.93);
        verify(stories, never()).save(any());
    }

    @Test
    @DisplayName("checkDuplicate: flags a near-duplicate without creating anything")
    void check_duplicate_flags_without_creating() {
        UUID existing = UUID.randomUUID();
        when(generationPort.isAvailable()).thenReturn(false);
        when(embeddingPort.isAvailable()).thenReturn(true);
        when(embeddingPort.embed(any())).thenReturn(new float[EmbeddingPort.DIMENSIONS]);
        when(stories.findMostSimilar(any(), any()))
                .thenReturn(Optional.of(new UserStoryRepository.SimilarStory(existing, 0.88)));

        StoryDuplicateCheck check = port.checkDuplicate(new ExternalIssueInput(
                PROJECT, "Maybe duplicate", "similar", null));

        assertThat(check.duplicate()).isTrue();
        assertThat(check.existingStoryId()).isEqualTo(existing);
        verify(stories, never()).save(any());
    }

    @Test
    @DisplayName("checkDuplicate: returns not-duplicate when the embedding model is unavailable")
    void check_duplicate_no_embedding() {
        when(embeddingPort.isAvailable()).thenReturn(false);

        StoryDuplicateCheck check = port.checkDuplicate(new ExternalIssueInput(
                PROJECT, "Anything", "x", null));

        assertThat(check.duplicate()).isFalse();
        assertThat(check.existingStoryId()).isNull();
    }
}
