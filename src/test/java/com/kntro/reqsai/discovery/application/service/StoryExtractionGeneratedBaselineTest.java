package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.GenerationResult;
import com.kntro.reqsai.discovery.application.port.SuggestionRepository;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("Application: Story Extraction freezes the text the AI proposed")
@ExtendWith(MockitoExtension.class)
class StoryExtractionGeneratedBaselineTest {

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

    @Test
    @DisplayName("a generated story keeps its proposal, criteria included, as baseline equal to what it says")
    void keepsTheProposal() {
        var gen = new GenerationResult.GeneratedStory("Login con Google", "usuario", "autenticarme con Google",
                "no necesitar contraseña", Priority.HIGH, 3,
                List.of(new GenerationResult.GeneratedCriterion("Cuenta válida", "tiene cuenta Google", "inicia sesión",
                        "accede")));
        when(stories.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var service = new StoryExtractionService(stories, deduplication, suggestions, embeddingPort, eventPublisher);

        Optional<UserStory> result = service.extractOne(gen, UUID.randomUUID(), UUID.randomUUID());

        assertThat(result).isPresent();
        assertThat(result.get().getGeneratedText()).isNotBlank().isEqualTo(result.get().currentText())
                .contains("Login con Google").contains("tiene cuenta Google");
        assertThat(result.get().getReviewedAt()).isNull();
    }
}
