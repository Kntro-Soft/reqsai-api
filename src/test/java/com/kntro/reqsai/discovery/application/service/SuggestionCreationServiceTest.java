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

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "dedupSimilarityThreshold", 0.84);
    }

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
    @DisplayName("EDGE_CASE carries the LLM's single Given/When/Then criterion (scenario label round-trips)")
    void edge_case_carries_the_criterion() {
        UserStory target = UserStoryMother.draft().withProjectId(projectId).build();
        GenerationResult.GeneratedStory gen = new GenerationResult.GeneratedStory(SuggestionType.EDGE_CASE,
                "Cuenta bloqueada", "usuario", "iniciar sesión", "acceder",
                Priority.MEDIUM, 2,
                List.of(new GenerationResult.GeneratedCriterion("Bloqueo por intentos",
                        "el usuario falló 5 intentos", "reintenta iniciar sesión",
                        "el sistema bloquea la cuenta")),
                "inicio de sesión", target.getId());
        when(embeddingPort.isAvailable()).thenReturn(true);
        when(embeddingPort.embed(any())).thenReturn(new float[]{0.1f});
        when(stories.findByIdAndProjectId(target.getId(), projectId)).thenReturn(Optional.of(target));
        when(suggestions.findAllBySessionIdAndStatus(any(), any())).thenReturn(List.of());
        when(suggestions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Suggestion> created = service.createSuggestions(resultOf(gen), sessionId, projectId);

        assertThat(created).hasSize(1);
        Suggestion edge = created.getFirst();
        assertThat(edge.getType()).isEqualTo(SuggestionType.EDGE_CASE);
        assertThat(edge.getDraftAcceptanceCriteria()).hasSize(1);
        var criterion = edge.getDraftAcceptanceCriteria().getFirst();
        assertThat(criterion.scenario()).isEqualTo("Bloqueo por intentos");
        assertThat(criterion.given()).isEqualTo("el usuario falló 5 intentos");
        assertThat(criterion.when()).isEqualTo("reintenta iniciar sesión");
        assertThat(criterion.then()).isEqualTo("el sistema bloquea la cuenta");
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

    @Test
    @DisplayName("should downgrade a NEW_STORY that near-duplicates an ACCEPTED story to UPDATE at the dedup bar")
    void should_downgrade_accepted_twin_to_update_at_dedup_threshold() {
        UUID acceptedId = UUID.randomUUID();
        when(embeddingPort.isAvailable()).thenReturn(true);
        when(embeddingPort.embed(any())).thenReturn(new float[]{0.1f});
        // 0.845 is below the strict 0.85 duplicate-story gate but at/above the 0.84 dedup bar:
        // the accepted-twin paraphrase that previously slipped through as a duplicate NEW is now caught.
        when(stories.findMostSimilar(any(), any()))
                .thenReturn(Optional.of(new UserStoryRepository.SimilarStory(acceptedId, 0.845)));
        when(suggestions.findAllBySessionIdAndStatus(any(), any())).thenReturn(List.of());
        when(suggestions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Suggestion> created = service.createSuggestions(
                resultOf(generated(SuggestionType.NEW_STORY, null)), sessionId, projectId);

        assertThat(created).hasSize(1);
        Suggestion s = created.getFirst();
        assertThat(s.getType()).isEqualTo(SuggestionType.UPDATE_STORY);
        assertThat(s.getTargetStoryId()).isEqualTo(acceptedId);
        // recordSimilarity fixes the "similarity always null" gap.
        assertThat(s.getSimilarity()).isEqualTo(0.845);
    }

    @Test
    @DisplayName("should keep a NEW_STORY when the closest accepted story is below the dedup bar")
    void should_keep_new_story_below_dedup_threshold() {
        when(embeddingPort.isAvailable()).thenReturn(true);
        when(embeddingPort.embed(any())).thenReturn(new float[]{0.1f});
        when(stories.findMostSimilar(any(), any()))
                .thenReturn(Optional.of(new UserStoryRepository.SimilarStory(UUID.randomUUID(), 0.80)));
        when(suggestions.findAllBySessionIdAndStatus(any(), any())).thenReturn(List.of());
        when(suggestions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Suggestion> created = service.createSuggestions(
                resultOf(generated(SuggestionType.NEW_STORY, null)), sessionId, projectId);

        assertThat(created).hasSize(1);
        assertThat(created.getFirst().getType()).isEqualTo(SuggestionType.NEW_STORY);
    }

    // ── Dedup ─────────────────────────────────────────────────────────────────

    private GenerationResult.GeneratedStory story(String title, String action) {
        return new GenerationResult.GeneratedStory(SuggestionType.NEW_STORY,
                title, "usuario", action, "beneficio", Priority.MEDIUM, 3, List.of(), null, null);
    }

    @Test
    @DisplayName("should drop a same-pass exact title duplicate before persisting (accent-insensitive)")
    void should_drop_same_pass_title_duplicate() {
        when(embeddingPort.isAvailable()).thenReturn(false); // isolate the string-dedup layer
        when(suggestions.findAllBySessionIdAndStatus(any(), any())).thenReturn(List.of());
        when(suggestions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GenerationResult result = new GenerationResult(List.of(
                story("Recuperar contraseña", "restablecer mi clave"),
                story("Recuperar contrasena", "restablecer la clave")   // same idea, no accent
        ), List.of());

        List<Suggestion> created = service.createSuggestions(result, sessionId, projectId);

        assertThat(created).hasSize(1);
        assertThat(created.getFirst().getDraftTitle()).isEqualTo("Recuperar contraseña");
    }

    @Test
    @DisplayName("should drop a cross-pass paraphrase via embedding similarity even when titles differ")
    void should_drop_cross_pass_embedding_duplicate() {
        // A pending suggestion from an earlier pass; the new draft paraphrases it.
        Suggestion pending = Suggestion.newStory(sessionId, projectId,
                "Autenticación de dos factores", "usuario", "usar 2FO", "seguridad", Priority.HIGH, 3);
        float[] twoFactorVec = new float[]{1f, 0f, 0f};

        when(embeddingPort.isAvailable()).thenReturn(true);
        // Everything 2FA-ish embeds to the same vector → cosine 1.0 ≥ 0.84.
        when(embeddingPort.embed(any())).thenReturn(twoFactorVec);
        when(suggestions.findAllBySessionIdAndStatus(any(), any())).thenReturn(List.of(pending));

        GenerationResult result = new GenerationResult(List.of(
                story("Soporte para autenticación de dos factores en inicio de sesión", "habilitar 2FA al iniciar sesión")
        ), List.of());

        List<Suggestion> created = service.createSuggestions(result, sessionId, projectId);

        assertThat(created).isEmpty(); // paraphrase suppressed
    }

    @Test
    @DisplayName("should keep a genuinely different draft even when a pending suggestion exists")
    void should_keep_distinct_draft() {
        Suggestion pending = Suggestion.newStory(sessionId, projectId,
                "Autenticación de dos factores", "usuario", "usar 2FA", "seguridad", Priority.HIGH, 3);

        when(embeddingPort.isAvailable()).thenReturn(true);
        // Pending 2FA embeds to one axis; the new export story to an orthogonal axis → cosine 0.
        when(embeddingPort.embed(any())).thenAnswer(inv -> {
            String text = inv.getArgument(0);
            return text.toLowerCase().contains("export")
                    ? new float[]{0f, 1f, 0f}
                    : new float[]{1f, 0f, 0f};
        });
        when(stories.findMostSimilar(any(), any())).thenReturn(Optional.empty());
        when(suggestions.findAllBySessionIdAndStatus(any(), any())).thenReturn(List.of(pending));
        when(suggestions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GenerationResult result = new GenerationResult(List.of(
                story("Exportar reportes a PDF", "exportar reportes en PDF")
        ), List.of());

        List<Suggestion> created = service.createSuggestions(result, sessionId, projectId);

        assertThat(created).hasSize(1);
        assertThat(created.getFirst().getDraftTitle()).isEqualTo("Exportar reportes a PDF");
    }

    @Test
    @DisplayName("cosineSimilarity: identical vectors → 1, orthogonal → 0")
    void cosine_similarity_math() {
        assertThat(SuggestionCreationService.cosineSimilarity(new float[]{1f, 2f, 3f}, new float[]{1f, 2f, 3f}))
                .isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(SuggestionCreationService.cosineSimilarity(new float[]{1f, 0f}, new float[]{0f, 1f}))
                .isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    @DisplayName("normalize: folds accents, punctuation and case for duplicate keys")
    void normalize_folds_accents_and_punctuation() {
        assertThat(SuggestionCreationService.normalize("  Iniciar Sesión!! "))
                .isEqualTo(SuggestionCreationService.normalize("iniciar sesion"));
        assertThat(SuggestionCreationService.normalize("   ")).isNull();
    }

    // ── Quality bar ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should skip an incoherent draft with a blank core field without failing the pass")
    void should_skip_incoherent_draft() {
        when(suggestions.findAllBySessionIdAndStatus(any(), any())).thenReturn(List.of());
        when(suggestions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(embeddingPort.isAvailable()).thenReturn(false);

        GenerationResult result = new GenerationResult(List.of(
                new GenerationResult.GeneratedStory(SuggestionType.NEW_STORY,
                        "Secure Decision Start", "", "", "debe ser seguro",
                        Priority.MEDIUM, 3, List.of(), null, null),
                story("Iniciar sesión en el sistema", "autenticarme")
        ), List.of());

        List<Suggestion> created = service.createSuggestions(result, sessionId, projectId);

        assertThat(created).hasSize(1);
        assertThat(created.getFirst().getDraftTitle()).isEqualTo("Iniciar sesión en el sistema");
    }
}
