package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.GenerationResult;
import com.kntro.reqsai.discovery.application.port.SuggestionRepository;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.event.SuggestionCreatedEvent;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.SuggestionType;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.discovery.mothers.UserStoryMother;
import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import com.kntro.reqsai.testsupport.AggregateEvents;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private SuggestionCreationService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new SuggestionCreationService(suggestions, stories, embeddingPort, new SuggestionDedupPolicy(0.84));
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
    @DisplayName("EDGE_CASE with no LLM target and only a weak embedding match leaves the target null")
    void edge_case_below_floor_has_no_target() {
        when(embeddingPort.isAvailable()).thenReturn(true);
        when(embeddingPort.embed(any())).thenReturn(new float[]{0.1f});
        // Nearest story is only 0.5 similar — below the 0.84 dedup floor, so it is NOT attached.
        when(stories.findMostSimilar(any(), any()))
                .thenReturn(Optional.of(new UserStoryRepository.SimilarStory(UUID.randomUUID(), 0.5)));
        when(suggestions.findAllBySessionIdAndStatus(any(), any())).thenReturn(List.of());
        when(suggestions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Suggestion> created = service.createSuggestions(
                resultOf(generated(SuggestionType.EDGE_CASE, null)), sessionId, projectId);

        assertThat(created).hasSize(1);
        assertThat(created.getFirst().getType()).isEqualTo(SuggestionType.EDGE_CASE);
        assertThat(created.getFirst().getTargetStoryId()).isNull();
    }

    @Test
    @DisplayName("EDGE_CASE with no LLM target and a strong embedding match attaches that story")
    void edge_case_above_floor_attaches_target() {
        UUID nearId = UUID.randomUUID();
        when(embeddingPort.isAvailable()).thenReturn(true);
        when(embeddingPort.embed(any())).thenReturn(new float[]{0.1f});
        when(stories.findMostSimilar(any(), any()))
                .thenReturn(Optional.of(new UserStoryRepository.SimilarStory(nearId, 0.9)));
        when(suggestions.findAllBySessionIdAndStatus(any(), any())).thenReturn(List.of());
        when(suggestions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Suggestion> created = service.createSuggestions(
                resultOf(generated(SuggestionType.EDGE_CASE, null)), sessionId, projectId);

        assertThat(created).hasSize(1);
        assertThat(created.getFirst().getTargetStoryId()).isEqualTo(nearId);
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

    /** An accepted story of this project with the same intent as {@link #generated} (same title). */
    private UserStory accepted2faStory() {
        return new UserStory(projectId, "Login con 2FA", "usuario", "autenticarme con un segundo factor",
                "proteger mi cuenta", Priority.HIGH, 3);
    }

    @Test
    @DisplayName("should still upgrade a near-duplicate NEW_STORY to UPDATE_STORY via embedding similarity")
    void should_upgrade_near_duplicate_new_story() {
        UserStory existing = accepted2faStory();
        when(embeddingPort.isAvailable()).thenReturn(true);
        when(embeddingPort.embed(any())).thenReturn(new float[]{0.1f});
        when(stories.findMostSimilar(any(), any()))
                .thenReturn(Optional.of(new UserStoryRepository.SimilarStory(existing.getId(), 0.93)));
        when(stories.findByIdAndProjectId(existing.getId(), projectId)).thenReturn(Optional.of(existing));
        when(suggestions.findAllBySessionIdAndStatus(any(), any())).thenReturn(List.of());
        when(suggestions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Suggestion> created = service.createSuggestions(
                resultOf(generated(SuggestionType.NEW_STORY, null)), sessionId, projectId);

        assertThat(created).hasSize(1);
        assertThat(created.getFirst().getType()).isEqualTo(SuggestionType.UPDATE_STORY);
        assertThat(created.getFirst().getTargetStoryId()).isEqualTo(existing.getId());
    }

    @Test
    @DisplayName("should downgrade a NEW_STORY that near-duplicates an ACCEPTED story to UPDATE at the dedup bar")
    void should_downgrade_accepted_twin_to_update_at_dedup_threshold() {
        UserStory accepted = accepted2faStory();
        when(embeddingPort.isAvailable()).thenReturn(true);
        when(embeddingPort.embed(any())).thenReturn(new float[]{0.1f});
        // 0.845 is below the strict 0.85 duplicate-story gate but at/above the 0.84 dedup bar:
        // the accepted-twin paraphrase that previously slipped through as a duplicate NEW is now caught.
        when(stories.findMostSimilar(any(), any()))
                .thenReturn(Optional.of(new UserStoryRepository.SimilarStory(accepted.getId(), 0.845)));
        when(stories.findByIdAndProjectId(accepted.getId(), projectId)).thenReturn(Optional.of(accepted));
        when(suggestions.findAllBySessionIdAndStatus(any(), any())).thenReturn(List.of());
        when(suggestions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Suggestion> created = service.createSuggestions(
                resultOf(generated(SuggestionType.NEW_STORY, null)), sessionId, projectId);

        assertThat(created).hasSize(1);
        Suggestion s = created.getFirst();
        assertThat(s.getType()).isEqualTo(SuggestionType.UPDATE_STORY);
        assertThat(s.getTargetStoryId()).isEqualTo(accepted.getId());
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

    @Test
    @DisplayName("should drop a draft linked to a still-PENDING suggestion when it only restates it")
    void should_drop_draft_targeting_pending_suggestion() {
        Suggestion pending = Suggestion.newStory(sessionId, projectId,
                "Login con 2FA", "usuario", "autenticarme con 2FA", "seguridad", Priority.HIGH, 3);
        when(embeddingPort.isAvailable()).thenReturn(false);
        when(suggestions.findAllBySessionIdAndStatus(any(), any())).thenReturn(List.of(pending));

        // The LLM points a restatement at the PENDING suggestion's own id (shown in the prompt).
        GenerationResult.GeneratedStory restatement = new GenerationResult.GeneratedStory(
                SuggestionType.UPDATE_STORY, "Login con 2FA", "usuario",
                "autenticarme con 2FA", "más seguridad", Priority.HIGH, 3,
                List.of(), null, pending.getId());

        List<Suggestion> created = service.createSuggestions(
                new GenerationResult(List.of(restatement), List.of()), sessionId, projectId);

        assertThat(created).isEmpty();
    }

    @Test
    @DisplayName("should keep, as a NEW_STORY with its criterion, a rule the LLM linked to a still-PENDING suggestion")
    void should_keep_rule_linked_to_pending_suggestion_as_new_story() {
        Suggestion pendingBooking = Suggestion.newStory(sessionId, projectId,
                "Reservar cita médica", "paciente", "reservar una cita médica desde el portal web",
                "ser atendido a tiempo", Priority.HIGH, 3);
        when(embeddingPort.isAvailable()).thenReturn(false);
        when(suggestions.findAllBySessionIdAndStatus(any(), any())).thenReturn(List.of(pendingBooking));
        when(suggestions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // The model attaches the penalty to the pending booking (as the prompt allows) — a pending suggestion
        // is not a story yet, so the rule used to be dropped here and never reached the analyst.
        GenerationResult.GeneratedStory penalty = new GenerationResult.GeneratedStory(
                SuggestionType.EDGE_CASE, "Penalidad por cancelación tardía", "paciente",
                "cancelar una cita médica", "se respeten los horarios", Priority.HIGH, 2,
                List.of(new GenerationResult.GeneratedCriterion("Cancelación tardía", "una cita reservada",
                        "el paciente la cancela con menos de 24 horas de anticipación",
                        "se le cobra una penalidad del 10 %")),
                "reserva de citas", pendingBooking.getId());

        List<Suggestion> created = service.createSuggestions(
                new GenerationResult(List.of(penalty), List.of()), sessionId, projectId);

        assertThat(created).singleElement().satisfies(s -> {
            assertThat(s.getType()).isEqualTo(SuggestionType.NEW_STORY);
            assertThat(s.getTargetStoryId()).isNull();
            assertThat(s.getDraftTitle()).isEqualTo("Penalidad por cancelación tardía");
            assertThat(s.getDraftAcceptanceCriteria()).singleElement()
                    .satisfies(c -> assertThat(c.then()).contains("10 %"));
        });
    }

    // ── Titles of kept drafts (production: three stories titled "Reserva de cita médica") ──

    /** The pending booking of the production meeting, as the analyst's queue holds it. */
    private Suggestion pendingBooking() {
        return Suggestion.newStory(sessionId, projectId, "Reserva de cita médica", "paciente",
                "reservar una cita médica desde el portal web eligiendo especialidad, médico y horario",
                "ser atendido a tiempo", Priority.HIGH, 3,
                List.of(new Suggestion.DraftCriterion("Reserva confirmada", "un horario disponible",
                        "el paciente lo reserva", "la cita queda confirmada")));
    }

    /**
     * The penalty draft as the model emitted it in production: an EDGE_CASE of the pending booking that
     * copies the booking's title.
     */
    private GenerationResult.GeneratedStory penaltyLinkedTo(Suggestion booking, @Nullable String scenario) {
        return new GenerationResult.GeneratedStory(SuggestionType.EDGE_CASE, booking.getDraftTitle(), "paciente",
                "cancelar una cita médica con menos de 24 horas de anticipación", "se respeten los horarios",
                Priority.HIGH, 2,
                List.of(new GenerationResult.GeneratedCriterion(scenario, "una cita reservada",
                        "el paciente la cancela con menos de 24 horas de anticipación",
                        "se le cobra una penalidad del 10 %")),
                "reserva de citas", booking.getId());
    }

    /** The doctor-notification draft: an UPDATE_STORY of the pending booking that copies its title. */
    private GenerationResult.GeneratedStory notificationLinkedTo(Suggestion booking, @Nullable String scenario) {
        return new GenerationResult.GeneratedStory(SuggestionType.UPDATE_STORY, booking.getDraftTitle(), "médico",
                "recibir un correo cuando un paciente reserva una cita conmigo", "organizar mi agenda",
                Priority.MEDIUM, 2,
                List.of(new GenerationResult.GeneratedCriterion(scenario, "un paciente reserva una cita conmigo",
                        "la reserva se registra", "recibo un correo con la fecha y la hora")),
                null, booking.getId());
    }

    @Test
    @DisplayName("production: the penalty and the notification linked to the pending booking get their own titles")
    void linked_drafts_never_reuse_the_pending_title() {
        Suggestion booking = pendingBooking();
        when(embeddingPort.isAvailable()).thenReturn(true);
        // Worst case, as in production: every draft of the meeting embeds to the same vector (cosine 1.0).
        when(embeddingPort.embed(any())).thenReturn(new float[]{1f, 0f, 0f});
        when(stories.findMostSimilar(any(), any())).thenReturn(Optional.empty());
        when(suggestions.findAllBySessionIdAndStatus(any(), any())).thenReturn(List.of(booking));
        when(suggestions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        GenerationResult result = new GenerationResult(List.of(
                penaltyLinkedTo(booking, "Penalidad por cancelación tardía"),
                notificationLinkedTo(booking, "Notificación al médico al reservar una cita")), List.of());

        List<Suggestion> created = service.createSuggestions(result, sessionId, projectId);

        assertThat(created).extracting(Suggestion::getDraftTitle).containsExactly(
                "Penalidad por cancelación tardía", "Notificación al médico al reservar una cita");
        assertThat(created).allSatisfy(s -> {
            assertThat(s.getType()).isEqualTo(SuggestionType.NEW_STORY);
            assertThat(s.getTargetStoryId()).isNull();
            assertThat(s.getDraftAcceptanceCriteria()).hasSize(1);
        });
        assertThat(created.get(0).getDraftAcceptanceCriteria().getFirst().then()).contains("10 %");
        assertThat(created.get(1).getDraftAcceptanceCriteria().getFirst().then()).contains("correo");
        // The live event carries the title and the criteria the database gets.
        assertThat(created).allSatisfy(s -> {
            SuggestionCreatedEvent event = createdEventOf(s);
            assertThat(event.type()).isEqualTo(SuggestionType.NEW_STORY);
            assertThat(event.draftTitle()).isEqualTo(s.getDraftTitle());
            assertThat(event.draftAcceptanceCriteria()).isEqualTo(s.getDraftAcceptanceCriteria());
        });
    }

    @Test
    @DisplayName("production: without scenario labels, the linked drafts are titled from their own action")
    void linked_drafts_without_scenario_are_titled_from_their_action() {
        Suggestion booking = pendingBooking();
        when(embeddingPort.isAvailable()).thenReturn(false);
        when(suggestions.findAllBySessionIdAndStatus(any(), any())).thenReturn(List.of(booking));
        when(suggestions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        GenerationResult result = new GenerationResult(List.of(
                penaltyLinkedTo(booking, null), notificationLinkedTo(booking, null)), List.of());

        List<Suggestion> created = service.createSuggestions(result, sessionId, projectId);

        assertThat(created).extracting(Suggestion::getDraftTitle).containsExactly(
                "Cancelar una cita médica con menos de 24 horas de anticipación",
                "Recibir un correo cuando un paciente reserva una cita conmigo");
        assertThat(created).allSatisfy(s -> assertThat(s.getDraftAcceptanceCriteria()).hasSize(1));
    }

    @Test
    @DisplayName("linked drafts that copy the whole booking narrative are titled from their criterion and both kept")
    void linked_drafts_copying_the_narrative_are_titled_from_their_criterion() {
        Suggestion booking = pendingBooking();
        when(embeddingPort.isAvailable()).thenReturn(false);
        when(suggestions.findAllBySessionIdAndStatus(any(), any())).thenReturn(List.of(booking));
        when(suggestions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // UPDATE_STORYs that copy title, role, action and benefit unchanged and add one criterion each,
        // as the prompt asks an update to do. With the booking's title, the second one used to be dropped
        // as a same-title repeat of the first.
        GenerationResult result = new GenerationResult(List.of(
                copyOf(booking, new GenerationResult.GeneratedCriterion(null, "una cita reservada",
                        "el paciente la cancela con menos de 24 horas de anticipación",
                        "se le cobra una penalidad del 10 %")),
                copyOf(booking, new GenerationResult.GeneratedCriterion(null, "un paciente reserva una cita",
                        "la reserva se registra", "el médico recibe un correo de aviso"))), List.of());

        List<Suggestion> created = service.createSuggestions(result, sessionId, projectId);

        assertThat(created).extracting(Suggestion::getDraftTitle).containsExactly(
                "Se le cobra una penalidad del 10 %", "El médico recibe un correo de aviso");
        assertThat(created).allSatisfy(s -> {
            assertThat(s.getType()).isEqualTo(SuggestionType.NEW_STORY);
            assertThat(s.getDraftAcceptanceCriteria()).hasSize(1);
        });
    }

    private static GenerationResult.GeneratedStory copyOf(Suggestion booking,
                                                          GenerationResult.GeneratedCriterion criterion) {
        return new GenerationResult.GeneratedStory(SuggestionType.UPDATE_STORY, booking.getDraftTitle(),
                booking.getDraftRole(), booking.getDraftAction(), booking.getDraftBenefit(), Priority.HIGH, 3,
                List.of(criterion), null, booking.getId());
    }

    @Test
    @DisplayName("titles are deterministic: the same pass over the same queue yields the same titles")
    void linked_titles_are_deterministic() {
        Suggestion booking = pendingBooking();
        when(embeddingPort.isAvailable()).thenReturn(false);
        when(suggestions.findAllBySessionIdAndStatus(any(), any())).thenReturn(List.of(booking));
        when(suggestions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        GenerationResult result = new GenerationResult(List.of(
                penaltyLinkedTo(booking, "Penalidad por cancelación tardía"),
                notificationLinkedTo(booking, null)), List.of());

        List<String> first = service.createSuggestions(result, sessionId, projectId).stream()
                .map(Suggestion::getDraftTitle).toList();
        List<String> second = service.createSuggestions(result, sessionId, projectId).stream()
                .map(Suggestion::getDraftTitle).toList();

        assertThat(first).isEqualTo(second).containsExactly("Penalidad por cancelación tardía",
                "Recibir un correo cuando un paciente reserva una cita conmigo");
    }

    @Test
    @DisplayName("two NEW_STORY drafts of one pass with the same title get distinct titles from their own content")
    void same_pass_new_stories_never_share_a_title() {
        when(embeddingPort.isAvailable()).thenReturn(false);
        when(suggestions.findAllBySessionIdAndStatus(any(), any())).thenReturn(List.of());
        when(suggestions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        GenerationResult result = new GenerationResult(List.of(
                new GenerationResult.GeneratedStory(SuggestionType.NEW_STORY, "Reserva de cita médica", "paciente",
                        "reservar una cita médica desde el portal web", "ser atendido a tiempo", Priority.HIGH, 3,
                        List.of(), null, null),
                new GenerationResult.GeneratedStory(SuggestionType.NEW_STORY, "Reserva de cita médica", "médico",
                        "recibir un correo cuando un paciente reserva una cita conmigo", "organizar mi agenda",
                        Priority.MEDIUM, 2,
                        List.of(new GenerationResult.GeneratedCriterion("Aviso de nueva reserva",
                                "un paciente reserva una cita", "la reserva se registra",
                                "el médico recibe un correo")), null, null)), List.of());

        List<Suggestion> created = service.createSuggestions(result, sessionId, projectId);

        assertThat(created).extracting(Suggestion::getDraftTitle)
                .containsExactly("Reserva de cita médica", "Aviso de nueva reserva");
    }

    @Test
    @DisplayName("a NEW_STORY kept next to a pending suggestion with the same title gets its own title")
    void new_story_never_takes_a_pending_title() {
        Suggestion booking = pendingBooking();
        when(embeddingPort.isAvailable()).thenReturn(false);
        when(suggestions.findAllBySessionIdAndStatus(any(), any())).thenReturn(List.of(booking));
        when(suggestions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // Not linked: the model re-used the pending title for another requirement.
        GenerationResult.GeneratedStory payment = new GenerationResult.GeneratedStory(SuggestionType.NEW_STORY,
                "Reserva de cita médica", "paciente", "pagar la consulta en línea al reservar la cita",
                "no hacer cola en la clínica", Priority.MEDIUM, 3, List.of(), null, null);

        List<Suggestion> created = service.createSuggestions(resultOf(payment), sessionId, projectId);

        assertThat(created).singleElement().satisfies(s -> {
            assertThat(s.getType()).isEqualTo(SuggestionType.NEW_STORY);
            assertThat(s.getDraftTitle()).isEqualTo("Pagar la consulta en línea al reservar la cita");
        });
    }

    private static SuggestionCreatedEvent createdEventOf(Suggestion s) {
        return AggregateEvents.of(s).stream()
                .filter(SuggestionCreatedEvent.class::isInstance)
                .map(SuggestionCreatedEvent.class::cast)
                .findFirst()
                .orElseThrow();
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
    @DisplayName("should drop a cross-pass restatement via embedding similarity even when titles differ")
    void should_drop_cross_pass_embedding_duplicate() {
        // A pending suggestion from an earlier pass; the new draft restates it in other order and inflection.
        Suggestion pending = Suggestion.newStory(sessionId, projectId,
                "Autenticación de dos factores", "usuario", "usar 2FA al iniciar sesión", "seguridad",
                Priority.HIGH, 3);
        float[] twoFactorVec = new float[]{1f, 0f, 0f};

        when(embeddingPort.isAvailable()).thenReturn(true);
        // Everything 2FA-ish embeds to the same vector → cosine 1.0 ≥ 0.84.
        when(embeddingPort.embed(any())).thenReturn(twoFactorVec);
        when(suggestions.findAllBySessionIdAndStatus(any(), any())).thenReturn(List.of(pending));

        GenerationResult result = new GenerationResult(List.of(
                story("Autenticación de dos factores en el inicio de sesión", "usar 2FA al iniciar la sesión")
        ), List.of());

        List<Suggestion> created = service.createSuggestions(result, sessionId, projectId);

        assertThat(created).isEmpty(); // restatement suppressed
    }

    @Test
    @DisplayName("should keep the booking, its cancellation penalty and the doctor's notification even at cosine 1.0")
    void should_keep_distinct_rules_of_one_domain_in_one_pass() {
        when(embeddingPort.isAvailable()).thenReturn(true);
        // Worst case: every draft of the meeting embeds to the SAME vector (cosine 1.0 between all of them).
        when(embeddingPort.embed(any())).thenReturn(new float[]{1f, 0f, 0f});
        when(stories.findMostSimilar(any(), any())).thenReturn(Optional.empty());
        when(suggestions.findAllBySessionIdAndStatus(any(), any())).thenReturn(List.of());
        when(suggestions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GenerationResult result = new GenerationResult(List.of(
                new GenerationResult.GeneratedStory(SuggestionType.NEW_STORY, "Reservar cita médica", "paciente",
                        "reservar una cita médica desde el portal web eligiendo especialidad, médico y horario",
                        "ser atendido a tiempo", Priority.HIGH, 3, List.of(), null, null),
                new GenerationResult.GeneratedStory(SuggestionType.NEW_STORY, "Penalidad por cancelación tardía",
                        "paciente", "cancelar una cita médica con menos de 24 horas pagando una penalidad del 10 %",
                        "se respeten los horarios", Priority.HIGH, 2, List.of(), null, null),
                new GenerationResult.GeneratedStory(SuggestionType.NEW_STORY, "Notificar reserva al médico",
                        "médico", "recibir un correo cuando se reserve una cita conmigo", "organizar mi agenda",
                        Priority.MEDIUM, 2, List.of(), null, null),
                // A restatement of the booking: the only draft that must be dropped.
                new GenerationResult.GeneratedStory(SuggestionType.NEW_STORY, "Reserva de citas médicas",
                        "paciente", "reservar una cita médica en el portal web", "ser atendido",
                        Priority.HIGH, 3, List.of(), null, null)
        ), List.of());

        List<Suggestion> created = service.createSuggestions(result, sessionId, projectId);

        assertThat(created).extracting(Suggestion::getDraftTitle).containsExactly(
                "Reservar cita médica", "Penalidad por cancelación tardía", "Notificar reserva al médico");
    }

    @Test
    @DisplayName("should keep a NEW penalty story instead of downgrading it to an UPDATE of the accepted booking")
    void should_not_downgrade_a_distinct_rule_into_the_similar_accepted_story() {
        UserStory booking = new UserStory(projectId, "Reservar cita médica", "paciente",
                "reservar una cita médica desde el portal web", "ser atendido a tiempo", Priority.HIGH, 3);
        when(embeddingPort.isAvailable()).thenReturn(true);
        when(embeddingPort.embed(any())).thenReturn(new float[]{0.1f});
        when(stories.findMostSimilar(any(), any()))
                .thenReturn(Optional.of(new UserStoryRepository.SimilarStory(booking.getId(), 0.90)));
        when(stories.findByIdAndProjectId(booking.getId(), projectId)).thenReturn(Optional.of(booking));
        when(suggestions.findAllBySessionIdAndStatus(any(), any())).thenReturn(List.of());
        when(suggestions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GenerationResult.GeneratedStory penalty = new GenerationResult.GeneratedStory(SuggestionType.NEW_STORY,
                "Penalidad por cancelación tardía", "paciente",
                "que se me cobre una penalidad del 10 % si cancelo con menos de 24 horas",
                "se respeten los horarios", Priority.HIGH, 2, List.of(), null, null);

        List<Suggestion> created = service.createSuggestions(resultOf(penalty), sessionId, projectId);

        assertThat(created).singleElement().satisfies(s -> {
            assertThat(s.getType()).isEqualTo(SuggestionType.NEW_STORY);
            assertThat(s.getTargetStoryId()).isNull();
        });
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

    // ── UPDATE_STORY criteria and no-op filter ────────────────────────────────

    private UserStory bookingWithCriterion() {
        UserStory booking = new UserStory(projectId, "Reservar cita médica", "paciente",
                "reservar una cita médica desde el portal web", "ser atendido a tiempo", Priority.HIGH, 3);
        booking.addAcceptanceCriterion("Reserva confirmada", "un horario disponible", "el paciente lo reserva",
                "la cita queda confirmada");
        return booking;
    }

    private GenerationResult.GeneratedStory updateOf(UserStory target, String action,
                                                     GenerationResult.GeneratedCriterion... criteria) {
        return new GenerationResult.GeneratedStory(SuggestionType.UPDATE_STORY,
                target.getTitle(), target.getRole(), action, target.getBenefit(), Priority.HIGH, 3,
                List.of(criteria), null, target.getId());
    }

    private void givenTarget(UserStory target) {
        when(embeddingPort.isAvailable()).thenReturn(false);
        when(stories.findByIdAndProjectId(target.getId(), projectId)).thenReturn(Optional.of(target));
        when(suggestions.findAllBySessionIdAndStatus(any(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("should drop a no-op UPDATE_STORY: same narrative and no new acceptance criteria")
    void should_drop_noop_update() {
        UserStory booking = bookingWithCriterion();
        givenTarget(booking);

        // Same narrative (only case/punctuation differ) and a criterion the story already has.
        GenerationResult.GeneratedStory noop = updateOf(booking, "Reservar una cita médica, desde el portal web.",
                new GenerationResult.GeneratedCriterion(null, "Un horario disponible", "el paciente lo reserva",
                        "la cita queda confirmada"));

        List<Suggestion> created = service.createSuggestions(resultOf(noop), sessionId, projectId);

        assertThat(created).isEmpty();
        org.mockito.Mockito.verify(suggestions, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("should keep an UPDATE_STORY with the same narrative that adds a criterion, carrying only that one")
    void should_keep_update_that_adds_a_criterion() {
        UserStory booking = bookingWithCriterion();
        givenTarget(booking);
        when(suggestions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GenerationResult.GeneratedStory update = updateOf(booking, booking.getAction(),
                new GenerationResult.GeneratedCriterion(null, "un horario disponible", "el paciente lo reserva",
                        "la cita queda confirmada"),
                new GenerationResult.GeneratedCriterion("Cancelación tardía", "una cita reservada",
                        "el paciente la cancela con menos de 24 horas", "se le cobra una penalidad del 10 %"));

        List<Suggestion> created = service.createSuggestions(resultOf(update), sessionId, projectId);

        assertThat(created).singleElement().satisfies(s -> {
            assertThat(s.getType()).isEqualTo(SuggestionType.UPDATE_STORY);
            assertThat(s.getTargetStoryId()).isEqualTo(booking.getId());
            assertThat(s.getDraftAcceptanceCriteria()).singleElement()
                    .satisfies(c -> assertThat(c.scenario()).isEqualTo("Cancelación tardía"));
        });
    }

    @Test
    @DisplayName("should keep an UPDATE_STORY that changes the narrative even without criteria")
    void should_keep_update_that_changes_the_narrative() {
        UserStory booking = bookingWithCriterion();
        givenTarget(booking);
        when(suggestions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GenerationResult.GeneratedStory update = updateOf(booking,
                "reservar una cita médica desde el portal web o desde la app móvil");

        List<Suggestion> created = service.createSuggestions(resultOf(update), sessionId, projectId);

        assertThat(created).singleElement()
                .satisfies(s -> assertThat(s.getType()).isEqualTo(SuggestionType.UPDATE_STORY));
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

    // ── Generated keywords ────────────────────────────────────────────────────

    @Test
    @DisplayName("should store a NEW_STORY's narrative and Given/When/Then without the keywords the model wrote")
    void should_strip_generated_keywords_from_new_story() {
        when(embeddingPort.isAvailable()).thenReturn(false);
        when(suggestions.findAllBySessionIdAndStatus(any(), any())).thenReturn(List.of());
        when(suggestions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var gen = new GenerationResult.GeneratedStory(SuggestionType.NEW_STORY,
                "Reserva de citas en línea", "Paciente que busca un horario disponible.",
                "Quiero reservar una cita desde la web o desde el celular.",
                "Para evitar largas colas y llamadas sin contestar.", Priority.HIGH, 3,
                List.of(new GenerationResult.GeneratedCriterion("Horario ocupado",
                                "Dado que un paciente está reservando una cita en línea",
                                "Cuando el primero confirma su reserva", "Entonces se queda con el horario."),
                        new GenerationResult.GeneratedCriterion("Planes de seguro",
                                "Dados los planes de seguro configurados", "cuando la devolución se procesa",
                                "entonces el reembolso se acredita en 5 días.")),
                null, null);

        List<Suggestion> created = service.createSuggestions(resultOf(gen), sessionId, projectId);

        assertThat(created).singleElement().satisfies(s -> {
            assertThat(s.getDraftTitle()).isEqualTo("Reserva de citas en línea");
            assertThat(s.getDraftRole()).isEqualTo("Paciente que busca un horario disponible");
            assertThat(s.getDraftAction()).isEqualTo("reservar una cita desde la web o desde el celular");
            assertThat(s.getDraftBenefit()).isEqualTo("evitar largas colas y llamadas sin contestar");
            assertThat(s.getDraftAcceptanceCriteria()).containsExactly(
                    new Suggestion.DraftCriterion("Horario ocupado",
                            "que un paciente está reservando una cita en línea", "el primero confirma su reserva",
                            "se queda con el horario"),
                    new Suggestion.DraftCriterion("Planes de seguro",
                            "los planes de seguro configurados", "la devolución se procesa",
                            "el reembolso se acredita en 5 días"));
        });
    }

    @Test
    @DisplayName("should store an EDGE_CASE criterion without the keywords the model wrote")
    void should_strip_generated_keywords_from_edge_case() {
        UserStory target = bookingWithCriterion();
        givenTarget(target);
        when(suggestions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var gen = new GenerationResult.GeneratedStory(SuggestionType.EDGE_CASE,
                "Pago fallido", "Como paciente", "Quiero reintentar el pago", "Para no perder la cita",
                Priority.HIGH, 2,
                List.of(new GenerationResult.GeneratedCriterion(null, "Dada una cita reservada",
                        "Cuando la pasarela de pago falla", "Entonces la cita queda separada 10 minutos.")),
                "pagos", target.getId());

        List<Suggestion> created = service.createSuggestions(resultOf(gen), sessionId, projectId);

        assertThat(created).singleElement().satisfies(s -> {
            assertThat(s.getType()).isEqualTo(SuggestionType.EDGE_CASE);
            assertThat(s.getDraftRole()).isEqualTo("paciente");
            assertThat(s.getDraftAction()).isEqualTo("reintentar el pago");
            assertThat(s.getDraftBenefit()).isEqualTo("no perder la cita");
            assertThat(s.getDraftAcceptanceCriteria()).containsExactly(new Suggestion.DraftCriterion(null,
                    "una cita reservada", "la pasarela de pago falla", "la cita queda separada 10 minutos"));
        });
    }

    @Test
    @DisplayName("should drop an UPDATE_STORY that only restates the story's criterion with Gherkin keywords")
    void should_drop_update_that_only_adds_keywords() {
        UserStory booking = bookingWithCriterion();
        givenTarget(booking);

        // The story says "un horario disponible / el paciente lo reserva / la cita queda confirmada". The
        // keywords alone used to make this criterion look new, so the no-op update reached the analyst.
        GenerationResult.GeneratedStory noop = updateOf(booking,
                "Quiero reservar una cita médica desde el portal web.",
                new GenerationResult.GeneratedCriterion(null, "Dado un horario disponible",
                        "Cuando el paciente lo reserva", "Entonces la cita queda confirmada."));

        List<Suggestion> created = service.createSuggestions(resultOf(noop), sessionId, projectId);

        assertThat(created).isEmpty();
        org.mockito.Mockito.verify(suggestions, org.mockito.Mockito.never()).save(any());
    }
}
