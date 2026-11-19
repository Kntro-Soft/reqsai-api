package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.billing.domain.exception.BillingError;
import com.kntro.reqsai.billing.domain.exception.BillingExceptions;
import com.kntro.reqsai.discovery.application.port.GenerationContext;
import com.kntro.reqsai.discovery.application.port.StoryDraft;
import com.kntro.reqsai.discovery.application.port.StoryImprovementPort;
import com.kntro.reqsai.discovery.application.port.StoryImprovementRequest;
import com.kntro.reqsai.discovery.application.port.StoryImprovementResult;
import com.kntro.reqsai.discovery.application.port.StoryImprovementResult.PendingValue;
import com.kntro.reqsai.discovery.application.port.UnparseableGenerationException;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.application.query.ProposeStoryImprovementQuery;
import com.kntro.reqsai.discovery.application.service.RealtimeSuggestionService;
import com.kntro.reqsai.discovery.application.service.StoryQualityReader;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryError;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.QualityCharacteristic;
import com.kntro.reqsai.discovery.domain.model.QualityFinding;
import com.kntro.reqsai.discovery.domain.model.StoryQualityAssessment;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.discovery.application.port.StoryQualityEvaluator;
import com.kntro.reqsai.discovery.infrastructure.quality.RuleBasedStoryQualityEvaluator;
import com.kntro.reqsai.shared.application.ai.AiFeature;
import com.kntro.reqsai.shared.application.ai.AiQuotaGuard;
import com.kntro.reqsai.shared.application.ai.AiUsageScope;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProposeStoryImprovementQueryHandler} with mocked ports: the happy path, the pending
 * values, the AI being unavailable or over its allowance, and a reply the model gets wrong.
 */
@DisplayName("Application: Propose an AI improvement of a story")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProposeStoryImprovementQueryHandlerTest {

    private static final String TOKEN = "[[tiempo máximo de respuesta]]";
    private static final UUID PROJECT = UUID.randomUUID();
    private static final GenerationContext CONTEXT = new GenerationContext("Reservas", null, List.of(), List.of(),
            List.of(), null, null, List.of("Cumplir la Ley 29733"), List.of(), List.of(), List.of());

    @Mock private UserStoryRepository stories;
    @Mock private StoryImprovementPort improvement;
    @Mock private StoryQualityReader qualityReader;
    @Mock private RealtimeSuggestionService contextService;
    @Mock private AiQuotaGuard quota;

    private final StoryQualityEvaluator evaluator = new RuleBasedStoryQualityEvaluator();
    private ProposeStoryImprovementQueryHandler handler;
    private UserStory story;

    @BeforeEach
    void setUp() {
        handler = new ProposeStoryImprovementQueryHandler(stories, improvement, qualityReader, evaluator,
                contextService, quota);
        story = new UserStory(PROJECT, "Exportar", "usuario", "exportar el backlog de forma rápida",
                "mejor control, etc.", Priority.MEDIUM, 3);
        when(stories.findByIdAndProjectId(story.getId(), PROJECT)).thenReturn(Optional.of(story));
        when(improvement.isAvailable()).thenReturn(true);
        when(contextService.profileContextFor(eq(PROJECT), anyString())).thenReturn(Optional.of(CONTEXT));
        StoryQualityAssessment assessment = StoryQualityAssessment.evaluate(story.getId(),
                evaluator.evaluate(story), Instant.now());
        when(qualityReader.read(any())).thenReturn(Map.of(story.getId(), assessment));
    }

    private static StoryImprovementResult proposal(String then, List<PendingValue> pending) {
        return new StoryImprovementResult(new StoryDraft("Exportar el backlog a CSV", "analista de cumplimiento",
                "exportar el backlog aprobado a un archivo CSV", "compartirlo con el cliente sin reescribirlo",
                List.of(new StoryDraft.CriterionDraft("camino feliz", "hay historias aprobadas en el backlog",
                        "el analista exporta el backlog", then))), pending,
                List.of("Se reemplazó «rápido» por un valor pendiente"));
    }

    private ProposeStoryImprovementQuery query() {
        return new ProposeStoryImprovementQuery(PROJECT, story.getId());
    }

    @Test
    @DisplayName("proposes without changing the story, with the pending values, the fixes and the expected quality")
    void happy_path() {
        String then = "el sistema responde en menos de " + TOKEN + " segundos";
        when(improvement.improve(any())).thenReturn(
                proposal(then, List.of(new PendingValue(TOKEN, "¿En cuántos segundos debe responder?"))));
        AtomicReference<AiUsageScope.Scope> scope = new AtomicReference<>();
        when(improvement.improve(any())).thenAnswer(invocation -> {
            scope.set(AiUsageScope.current());
            return proposal(then, List.of(new PendingValue(TOKEN, "¿En cuántos segundos debe responder?")));
        });

        StoryImprovementProposal result = handler.handle(query());

        assertThat(result.storyId()).isEqualTo(story.getId());
        assertThat(result.proposal().title()).isEqualTo("Exportar el backlog a CSV");
        assertThat(result.proposal().acceptanceCriteria().getFirst().then()).contains(TOKEN);
        assertThat(result.pendingValues()).containsExactly(new PendingValue(TOKEN, "¿En cuántos segundos debe responder?"));
        assertThat(result.fixes()).containsExactly("Se reemplazó «rápido» por un valor pendiente");
        // The expected quality is the rule-based evaluation of the proposal: it reads the proposal, not the story.
        assertThat(result.expectedQuality().getStoryId()).isEqualTo(story.getId());
        assertThat(result.expectedQuality().getFindings()).hasSize(4);
        assertThat(result.expectedQuality().getScore()).isEqualTo(100);
        // Nothing was changed or stored.
        assertThat(story.getTitle()).isEqualTo("Exportar");
        assertThat(story.getAcceptanceCriteria()).isEmpty();
        verify(stories, never()).save(any());
        // The call is metered as the story improvement of this project, after the allowance check.
        assertThat(scope.get()).isEqualTo(new AiUsageScope.Scope(AiFeature.STORY_IMPROVEMENT, PROJECT));
        verify(quota).assertAvailable();
    }

    @Test
    @DisplayName("gives the model the story, its unmet findings, the project context and the meeting quotes")
    void builds_the_request() {
        story.addSource(UUID.randomUUID(), 3, "necesito exportar el backlog rápido");
        when(improvement.improve(any())).thenReturn(proposal("el sistema muestra un mensaje", List.of()));

        handler.handle(query());

        ArgumentCaptor<StoryImprovementRequest> request = ArgumentCaptor.forClass(StoryImprovementRequest.class);
        verify(improvement).improve(request.capture());
        assertThat(request.getValue().story().title()).isEqualTo("Exportar");
        assertThat(request.getValue().story().action()).isEqualTo("exportar el backlog de forma rápida");
        assertThat(request.getValue().findings()).extracting(StoryImprovementRequest.Finding::characteristic)
                .contains("AMBIGUITY", "VERIFIABILITY");
        assertThat(request.getValue().findings()).allSatisfy(f -> assertThat(f.suggestion()).isNotBlank());
        assertThat(request.getValue().context()).isSameAs(CONTEXT);
        assertThat(request.getValue().sourceQuotes()).containsExactly("necesito exportar el backlog rápido");
    }

    @Test
    @DisplayName("a story from before fragments were kept contributes its origin quote; a manual one has none")
    void origin_quote_fallback() {
        story.recordOrigin(null, null, "lo dijo el cliente", null);
        when(improvement.improve(any())).thenReturn(proposal("el sistema muestra un mensaje", List.of()));

        handler.handle(query());

        ArgumentCaptor<StoryImprovementRequest> request = ArgumentCaptor.forClass(StoryImprovementRequest.class);
        verify(improvement).improve(request.capture());
        assertThat(request.getValue().sourceQuotes()).containsExactly("lo dijo el cliente");
    }

    @Test
    @DisplayName("a story that is not in the project is a 404 and the model is never called")
    void story_not_found() {
        UUID other = UUID.randomUUID();
        when(stories.findByIdAndProjectId(other, PROJECT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new ProposeStoryImprovementQuery(PROJECT, other)))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.error()).isEqualTo(DiscoveryError.USER_STORY_NOT_FOUND));
        verifyNoInteractions(improvement, quota);
    }

    @Test
    @DisplayName("with no AI available it answers REQUIREMENT_GENERATION_FAILED like the other AI endpoints")
    void ai_unavailable() {
        when(improvement.isAvailable()).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(query()))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.error()).isEqualTo(DiscoveryError.REQUIREMENT_GENERATION_FAILED));
        verify(improvement, never()).improve(any());
    }

    @Test
    @DisplayName("with the monthly allowance spent it fails before calling the model, with the guard's own error")
    void over_the_allowance() {
        DomainException exceeded = BillingExceptions.aiQuotaExceeded("FREE", 100_000, 100_000, Instant.now());
        doThrow(exceeded).when(quota).assertAvailable();

        assertThatThrownBy(() -> handler.handle(query()))
                .isSameAs(exceeded)
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.error()).isEqualTo(BillingError.AI_QUOTA_EXCEEDED));
        verify(improvement, never()).improve(any());
    }

    @Test
    @DisplayName("an unreadable reply is asked for once more and the second one is used")
    void retries_an_unreadable_reply_once() {
        when(improvement.improve(any()))
                .thenThrow(new UnparseableGenerationException("prose", null))
                .thenReturn(proposal("el sistema muestra un mensaje", List.of()));

        StoryImprovementProposal result = handler.handle(query());

        assertThat(result.proposal().title()).isEqualTo("Exportar el backlog a CSV");
        verify(improvement, times(2)).improve(any());
    }

    @Test
    @DisplayName("two unreadable replies fail cleanly with REQUIREMENT_GENERATION_FAILED")
    void gives_up_after_two_unreadable_replies() {
        when(improvement.improve(any())).thenThrow(new UnparseableGenerationException("prose", null));

        assertThatThrownBy(() -> handler.handle(query()))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.error()).isEqualTo(DiscoveryError.REQUIREMENT_GENERATION_FAILED));
        verify(improvement, times(2)).improve(any());
    }

    @Test
    @DisplayName("a token the model wrote but did not declare is rejected and asked again, never shown")
    void undeclared_token_is_retried() {
        String then = "el sistema responde en menos de " + TOKEN + " segundos";
        when(improvement.improve(any()))
                .thenReturn(proposal(then, List.of()))
                .thenReturn(proposal(then, List.of(new PendingValue(TOKEN, "¿Cuántos segundos?"))));

        StoryImprovementProposal result = handler.handle(query());

        assertThat(result.pendingValues()).containsExactly(new PendingValue(TOKEN, "¿Cuántos segundos?"));
        verify(improvement, times(2)).improve(any());
    }

    @Test
    @DisplayName("a proposal over the length limits is rejected, not cut")
    void over_long_proposal_is_rejected() {
        when(improvement.improve(any())).thenReturn(new StoryImprovementResult(
                new StoryDraft("t".repeat(201), "r", "a", "b", List.of()), List.of(), List.of()));

        assertThatThrownBy(() -> handler.handle(query()))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.error()).isEqualTo(DiscoveryError.REQUIREMENT_GENERATION_FAILED));
    }

    @Test
    @DisplayName("while a token is pending the expected quality is evaluated as it is: the token is not ambiguity by itself")
    void tokens_are_not_ambiguity_by_themselves() {
        String then = "el sistema responde en menos de " + TOKEN + " segundos";
        when(improvement.improve(any())).thenReturn(
                proposal(then, List.of(new PendingValue(TOKEN, "¿Cuántos segundos?"))));

        StoryImprovementProposal result = handler.handle(query());

        QualityFinding ambiguity = result.expectedQuality().getFindings().stream()
                .filter(f -> f.characteristic() == QualityCharacteristic.AMBIGUITY).findFirst().orElseThrow();
        QualityFinding verifiability = result.expectedQuality().getFindings().stream()
                .filter(f -> f.characteristic() == QualityCharacteristic.VERIFIABILITY).findFirst().orElseThrow();
        assertThat(ambiguity.met()).isTrue();
        assertThat(verifiability.met()).isTrue();
    }
}
