package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.GenerationResult;
import com.kntro.reqsai.discovery.application.port.SuggestionRepository;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.event.UserStoryNearDuplicateDetectedEvent;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryError;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.SuggestionType;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("Application: Story Extraction Service")
@ExtendWith(MockitoExtension.class)
class StoryExtractionServiceTest {

    @Mock
    private UserStoryRepository stories;
    @Mock
    private UserStoryDeduplicationService deduplication;
    @Mock
    private SuggestionRepository suggestions;
    @Mock
    private EmbeddingPort embeddingPort;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    private StoryExtractionService service;

    @BeforeEach
    void setUp() {
        service = new StoryExtractionService(stories, deduplication, suggestions, embeddingPort, eventPublisher);
    }

    @Test
    @DisplayName("should persist story and return it when no duplicate found")
    void should_persist_when_no_duplicate() {
        UUID sessionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        var gen = new GenerationResult.GeneratedStory(
                "Login con Google", "usuario", "autenticarme con Google", "no necesitar contraseña",
                Priority.HIGH, 3, List.of());
        when(stories.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<UserStory> result = service.extractOne(gen, sessionId, projectId);

        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("Login con Google");
        assertThat(result.get().getSessionId()).isEqualTo(sessionId);
        verify(deduplication).embedAndGuardDuplicates(any(UserStory.class));
        verify(stories).save(any(UserStory.class));
    }

    @Test
    @DisplayName("should publish event and drop when a duplicate is detected but no target resolves")
    void should_drop_when_no_target_resolves() {
        UUID sessionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        var gen = new GenerationResult.GeneratedStory(
                "Dup story", "u", "a", "b", Priority.LOW, 1, List.of());
        doThrow(new DomainException(DiscoveryError.DUPLICATE_USER_STORY, "sim=0.92"))
                .when(deduplication).embedAndGuardDuplicates(any(UserStory.class));
        // embeddingPort.isAvailable() defaults to false → no target → drop (legacy event only)

        Optional<UserStory> result = service.extractOne(gen, sessionId, projectId);

        assertThat(result).isEmpty();
        verify(stories, never()).save(any());
        verify(suggestions, never()).save(any());
        verify(eventPublisher).publishEvent(any(UserStoryNearDuplicateDetectedEvent.class));
    }

    @Test
    @DisplayName("should raise an UPDATE_STORY duplicate-alert suggestion when a similar story exists")
    void should_raise_duplicate_alert_suggestion() {
        UUID sessionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        var gen = new GenerationResult.GeneratedStory(
                "Dup story", "u", "a", "b", Priority.LOW, 1, List.of());
        doThrow(new DomainException(DiscoveryError.DUPLICATE_USER_STORY, "sim"))
                .when(deduplication).embedAndGuardDuplicates(any(UserStory.class));
        when(embeddingPort.isAvailable()).thenReturn(true);
        when(embeddingPort.embed(any())).thenReturn(new float[]{0.1f});
        when(stories.findMostSimilar(eq(projectId), any()))
                .thenReturn(Optional.of(new UserStoryRepository.SimilarStory(targetId, 0.93)));

        Optional<UserStory> result = service.extractOne(gen, sessionId, projectId);

        assertThat(result).isEmpty();
        verify(stories, never()).save(any());
        ArgumentCaptor<Suggestion> captor = ArgumentCaptor.forClass(Suggestion.class);
        verify(suggestions).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(SuggestionType.UPDATE_STORY);
        assertThat(captor.getValue().getTargetStoryId()).isEqualTo(targetId);
        assertThat(captor.getValue().getSimilarity()).isEqualTo(0.93);
        verify(eventPublisher).publishEvent(any(UserStoryNearDuplicateDetectedEvent.class));
    }

    @Test
    @DisplayName("should return empty without publishing event when story construction fails validation")
    void should_skip_without_event_on_construction_failure() {
        UUID sessionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        var gen = new GenerationResult.GeneratedStory(
                "", "u", "a", "b", Priority.LOW, 1, List.of()); // blank title triggers domain validation

        Optional<UserStory> result = service.extractOne(gen, sessionId, projectId);

        assertThat(result).isEmpty();
        verify(stories, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(UserStoryNearDuplicateDetectedEvent.class));
    }

    @Test
    @DisplayName("should return present for a valid story and empty for a duplicate independently")
    void should_return_present_or_empty_per_story_independently() {
        UUID sessionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        var valid     = new GenerationResult.GeneratedStory("Good story", "u", "a", "b", Priority.MEDIUM, 2, List.of());
        var duplicate = new GenerationResult.GeneratedStory("Dup story",  "u", "a", "b", Priority.LOW,    1, List.of());
        when(stories.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(deduplication).embedAndGuardDuplicates(argThat(s -> "Good story".equals(s.getTitle())));
        doThrow(new DomainException(DiscoveryError.DUPLICATE_USER_STORY, "dup"))
                .when(deduplication).embedAndGuardDuplicates(argThat(s -> "Dup story".equals(s.getTitle())));

        Optional<UserStory> validResult     = service.extractOne(valid,     sessionId, projectId);
        Optional<UserStory> duplicateResult = service.extractOne(duplicate, sessionId, projectId);

        assertThat(validResult).isPresent();
        assertThat(validResult.get().getTitle()).isEqualTo("Good story");
        assertThat(duplicateResult).isEmpty();
    }
}
