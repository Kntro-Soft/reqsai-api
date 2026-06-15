package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.EmbeddingPort;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.application.service.UserStoryDeduplicationService;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryError;
import com.kntro.reqsai.discovery.domain.model.StoryStatus;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.discovery.mothers.CreateUserStoryCommandMother;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CreateUserStoryCommandHandler} with a mocked repository and embedding port.
 *
 * @see CreateUserStoryCommandHandler
 */
@DisplayName("Application: Create User Story")
@ExtendWith(MockitoExtension.class)
class CreateUserStoryCommandHandlerTest {

    @Mock
    private UserStoryRepository stories;
    @Mock
    private EmbeddingPort embeddingPort;
    private CreateUserStoryCommandHandler handler;

    @BeforeEach
    void setUp() {
        UserStoryDeduplicationService deduplication = new UserStoryDeduplicationService(stories, embeddingPort);
        handler = new CreateUserStoryCommandHandler(stories, deduplication);
    }

    @Test
    @DisplayName("should create the story in DRAFT and persist it (no embedding model configured)")
    void should_create_in_draft() {
        // Arrange — embeddingPort.isAvailable() defaults to false, so dedup is skipped
        when(stories.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var command = CreateUserStoryCommandMother.valid();

        // Act
        UserStory story = handler.handle(command);

        // Assert
        assertThat(story.getStatus()).isEqualTo(StoryStatus.DRAFT);
        assertThat(story.getProjectId()).isEqualTo(command.projectId());
        assertThat(story.getTitle()).isEqualTo(command.title());
        assertThat(story.getEmbedding()).isNull();
        verify(stories).save(any(UserStory.class));
    }

    @Test
    @DisplayName("should reject a blank title before persisting")
    void should_reject_blank_title() {
        // Act & Assert
        assertThatThrownBy(() -> handler.handle(CreateUserStoryCommandMother.withBlankTitle()))
                .isInstanceOf(DomainException.class);
        verify(stories, never()).save(any());
    }

    @Test
    @DisplayName("should embed the story and persist it when no near-duplicate exists")
    void should_embed_and_persist_below_threshold() {
        // Arrange
        when(embeddingPort.isAvailable()).thenReturn(true);
        when(embeddingPort.embed(any())).thenReturn(new float[UserStory.EMBEDDING_DIMENSIONS]);
        when(stories.highestSimilarity(any(), any())).thenReturn(Optional.of(0.42));
        when(stories.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        UserStory story = handler.handle(CreateUserStoryCommandMother.valid());

        // Assert
        assertThat(story.getEmbedding()).hasSize(UserStory.EMBEDDING_DIMENSIONS);
        verify(stories).save(any(UserStory.class));
    }

    @Test
    @DisplayName("should reject a near-duplicate story (similarity >= threshold) without persisting")
    void should_reject_near_duplicate() {
        // Arrange
        when(embeddingPort.isAvailable()).thenReturn(true);
        when(embeddingPort.embed(any())).thenReturn(new float[UserStory.EMBEDDING_DIMENSIONS]);
        when(stories.highestSimilarity(any(), any())).thenReturn(Optional.of(0.91));

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(CreateUserStoryCommandMother.valid()))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).error())
                        .isEqualTo(DiscoveryError.DUPLICATE_USER_STORY));
        verify(stories, never()).save(any());
    }
}
