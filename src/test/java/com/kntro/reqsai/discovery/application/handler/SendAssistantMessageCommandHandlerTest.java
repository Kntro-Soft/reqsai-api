package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.SendAssistantMessageCommand;
import com.kntro.reqsai.discovery.application.port.AssistantMessageRepository;
import com.kntro.reqsai.discovery.application.port.AssistantReply;
import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.GenerationContext;
import com.kntro.reqsai.discovery.application.port.GenerationResult;
import com.kntro.reqsai.discovery.application.port.RequirementGenerationPort;
import com.kntro.reqsai.discovery.application.port.SuggestionRepository;
import com.kntro.reqsai.discovery.application.port.UnparseableGenerationException;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.application.service.RealtimeSuggestionService;
import com.kntro.reqsai.discovery.application.service.SuggestionCreationService;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryError;
import com.kntro.reqsai.discovery.domain.model.AssistantMessage;
import com.kntro.reqsai.discovery.domain.model.AssistantMessageRole;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SendAssistantMessageCommandHandler}: a question is answered without creating
 * suggestions, a requirement goes through extraction into project-level (session-less) suggestions, and
 * the reply always lands in the chat.
 */
@DisplayName("Application: Send assistant chat message")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SendAssistantMessageCommandHandlerTest {

    private static final UUID PROJECT = UUID.randomUUID();
    private static final GenerationContext CONTEXT = new GenerationContext("Restaurante", null, List.of(),
            List.of(), List.of(), null, null, List.of(), List.of(), List.of(), List.of());
    private static final GenerationResult ONE_STORY = new GenerationResult(List.of(
            new GenerationResult.GeneratedStory("Cancelar reserva", "comensal", "cancelar mi reserva",
                    "liberar la mesa", Priority.HIGH, 2, List.of())));

    @Mock private AssistantMessageRepository messages;
    @Mock private RequirementGenerationPort generation;
    @Mock private RealtimeSuggestionService contextService;
    @Mock private SuggestionCreationService suggestionCreation;
    @Mock private SuggestionRepository suggestions;
    @Mock private UserStoryRepository stories;
    @Mock private DiscoverySessionRepository sessions;
    @InjectMocks private SendAssistantMessageCommandHandler handler;

    @BeforeEach
    void setUp() {
        when(generation.isAvailable()).thenReturn(true);
        when(messages.findLatestByProjectId(eq(PROJECT), anyInt())).thenReturn(List.of());
        when(messages.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(contextService.contextFor(eq(PROJECT), anyString(), anyList())).thenReturn(Optional.of(CONTEXT));
        when(stories.findAllByProjectId(eq(PROJECT), any(), any())).thenReturn(Page.empty());
        when(stories.findAllByProjectId(eq(PROJECT), any(org.springframework.data.domain.Pageable.class))).thenReturn(Page.empty());
        when(suggestions.findAllByProjectIdAndStatus(eq(PROJECT), any(), any())).thenReturn(Page.empty());
        when(sessions.findAllByProjectId(eq(PROJECT), any())).thenReturn(Page.empty());
    }

    @Test
    @DisplayName("answers a question without creating suggestions")
    void answers_question() {
        when(generation.converse(anyString(), anyList(), any(), any()))
                .thenReturn(new AssistantReply("Aún no hay historias.", null, "es"));

        AssistantExchange exchange = handler.handle(new SendAssistantMessageCommand(PROJECT, "¿Cuántas historias hay?"));

        assertThat(exchange.question().message().getRole()).isEqualTo(AssistantMessageRole.ANALYST);
        assertThat(exchange.answer().message().getContent()).isEqualTo("Aún no hay historias.");
        assertThat(exchange.answer().suggestions()).isEmpty();
        verify(generation, never()).generate(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("turns a requirement into session-less suggestions linked to the reply")
    void turns_requirement_into_suggestions() {
        when(generation.converse(anyString(), anyList(), any(), any()))
                .thenReturn(new AssistantReply("Entendido.", "El comensal puede cancelar su reserva.", "es"));
        when(generation.generate("El comensal puede cancelar su reserva.", "es", CONTEXT)).thenReturn(ONE_STORY);
        Suggestion created = Suggestion.newStory(null, PROJECT, "Cancelar reserva", "comensal",
                "cancelar mi reserva", "liberar la mesa", Priority.HIGH, 2);
        when(suggestionCreation.createSuggestions(eq(ONE_STORY), isNull(), eq(PROJECT))).thenReturn(List.of(created));

        AssistantExchange exchange = handler.handle(new SendAssistantMessageCommand(PROJECT, "quiero que puedan cancelar"));

        assertThat(exchange.answer().suggestions()).containsExactly(created);
        assertThat(exchange.answer().message().getSuggestionIds()).containsExactly(created.getId());
        assertThat(created.getSessionId()).isNull();
    }

    @Test
    @DisplayName("explains when a requirement is already covered and nothing new was raised")
    void notes_covered_requirement() {
        when(generation.converse(anyString(), anyList(), any(), any()))
                .thenReturn(new AssistantReply("Entendido.", "Reservar mesa por Internet.", "es"));
        when(generation.generate(anyString(), anyString(), any())).thenReturn(ONE_STORY);
        when(suggestionCreation.createSuggestions(any(), isNull(), eq(PROJECT))).thenReturn(List.of());

        AssistantExchange exchange = handler.handle(new SendAssistantMessageCommand(PROJECT, "quiero reservar por web"));

        assertThat(exchange.answer().message().getContent())
                .startsWith("Entendido.")
                .contains("ya está cubierto");
    }

    @Test
    @DisplayName("asks the model again once when the reply is not the JSON contract, then fails cleanly")
    void retries_unparseable_reply_once() {
        when(generation.converse(anyString(), anyList(), any(), any()))
                .thenThrow(new UnparseableGenerationException("prose", null));

        assertThatThrownBy(() -> handler.handle(new SendAssistantMessageCommand(PROJECT, "hola")))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).error()).isEqualTo(DiscoveryError.REQUIREMENT_GENERATION_FAILED));
        verify(generation, times(2)).converse(anyString(), anyList(), any(), any());
    }

    @Test
    @DisplayName("refuses when the AI is not available")
    void refuses_when_unavailable() {
        when(generation.isAvailable()).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(new SendAssistantMessageCommand(PROJECT, "hola")))
                .isInstanceOf(DomainException.class);
        verify(messages, never()).save(any(AssistantMessage.class));
    }
}
