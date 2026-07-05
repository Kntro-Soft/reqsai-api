package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.*;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.SuggestionStatus;
import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.discovery.mothers.DiscoverySessionBuilder;
import com.kntro.reqsai.discovery.mothers.UserStoryMother;
import com.kntro.reqsai.workspace.api.GlossaryTermSnapshot;
import com.kntro.reqsai.workspace.api.ProjectSnapshot;
import com.kntro.reqsai.workspace.api.WorkspaceModuleApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.isNull;

@DisplayName("Application: RealtimeSuggestionService")
@ExtendWith(MockitoExtension.class)
class RealtimeSuggestionServiceTest {

    @Mock private DiscoverySessionRepository sessions;
    @Mock private TranscriptSegmentRepository segments;
    @Mock private WorkspaceModuleApi workspaceApi;
    @Mock private RequirementGenerationPort generation;
    @Mock private SuggestionCreationService suggestionCreation;
    @Mock private EmbeddingPort embeddingPort;
    @Mock private UserStoryRepository stories;
    @Mock private SuggestionRepository suggestions;
    @Mock private UserStoryReindexService reindexService;

    @InjectMocks
    private RealtimeSuggestionService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "contextTopK", 5);
        ReflectionTestUtils.setField(service, "minTranscriptChars", 0);
    }

    private DiscoverySession buildSession(UUID projectId) {
        return DiscoverySessionBuilder.aSession().withProjectId(projectId).withLanguage("es-PE").build();
    }

    private TranscriptSegment finalSegment(UUID sessionId, int sequence, String text) {
        return new TranscriptSegment(sessionId, sequence, null, text, 0L, 100L, true);
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("should use similarity search when embedding is available")
        void should_use_similarity_search_when_embedding_available() {
            UUID projectId = UUID.randomUUID();
            DiscoverySession session = buildSession(projectId);
            UUID sessionId = session.getId();
            float[] vector = new float[]{0.1f, 0.2f};

            ProjectSnapshot snapshot = new ProjectSnapshot(
                    projectId, "PayApp", "Payment platform",
                    List.of("Java"), List.of("Spring"), List.of("Web"), List.of("PostgreSQL"),
                    "Microservices", "Finance",
                    List.of("PCI-DSS compliant"),
                    List.of(new GlossaryTermSnapshot("Sprint", "Iteration"))
            );
            GenerationResult result = new GenerationResult(List.of(
                    new GenerationResult.GeneratedStory("Login Google", "usuario", "login", "acceso", Priority.HIGH, 3, List.of())
            ));
            when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
            when(segments.findFinalBySessionIdAfter(sessionId, 0)).thenReturn(
                    List.of(finalSegment(sessionId, 5, "El cliente quiere login."))
            );
            when(generation.isAvailable()).thenReturn(true);
            when(embeddingPort.isAvailable()).thenReturn(true);
            when(embeddingPort.embed(any())).thenReturn(vector);
            when(workspaceApi.findRelevantContext(eq(projectId), eq(vector), eq(5))).thenReturn(Optional.of(snapshot));
            when(generation.generate(any(), any(), any(GenerationContext.class))).thenReturn(result);

            service.suggest(sessionId);

            verify(workspaceApi).findRelevantContext(projectId, vector, 5);
            verify(workspaceApi, never()).findProjectSnapshot(any());
            var contextCaptor = ArgumentCaptor.forClass(GenerationContext.class);
            verify(generation).generate(any(), eq("es-PE"), contextCaptor.capture());
            assertThat(contextCaptor.getValue().projectName()).isEqualTo("PayApp");
            assertThat(contextCaptor.getValue().constraints()).containsExactly("PCI-DSS compliant");
            verify(sessions).save(session);
            assertThat(session.getLastSuggestedSequence()).isEqualTo(5);
        }

        @Test
        @DisplayName("should fall back to findProjectSnapshot when embedding is unavailable")
        void should_fall_back_to_snapshot_when_embedding_unavailable() {
            UUID projectId = UUID.randomUUID();
            DiscoverySession session = buildSession(projectId);
            UUID sessionId = session.getId();

            ProjectSnapshot snapshot = new ProjectSnapshot(
                    projectId, "PayApp", null,
                    List.of(), List.of(), List.of(), List.of(),
                    "Monolith", "Finance",
                    List.of(), List.of()
            );

            when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
            when(segments.findFinalBySessionIdAfter(sessionId, 0)).thenReturn(
                    List.of(finalSegment(sessionId, 5, "texto"))
            );
            when(generation.isAvailable()).thenReturn(true);
            when(embeddingPort.isAvailable()).thenReturn(false);
            when(workspaceApi.findProjectSnapshot(projectId)).thenReturn(Optional.of(snapshot));
            when(generation.generate(any(), any(), any(GenerationContext.class))).thenReturn(new GenerationResult(List.of()));

            service.suggest(sessionId);

            verify(workspaceApi).findProjectSnapshot(projectId);
            verify(workspaceApi, never()).findRelevantContext(any(), any(), anyInt());
        }

        @Test
        @DisplayName("should generate without context when workspace returns empty")
        void should_generate_without_context_when_workspace_empty() {
            UUID projectId = UUID.randomUUID();
            DiscoverySession session = buildSession(projectId);
            UUID sessionId = session.getId();

            when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
            when(segments.findFinalBySessionIdAfter(sessionId, 0)).thenReturn(
                    List.of(finalSegment(sessionId, 5, "El cliente quiere reportes."))
            );
            when(generation.isAvailable()).thenReturn(true);
            when(embeddingPort.isAvailable()).thenReturn(true);
            when(embeddingPort.embed(any())).thenReturn(new float[]{0.1f});
            when(workspaceApi.findRelevantContext(any(), any(), anyInt())).thenReturn(Optional.empty());
            when(generation.generate(any(), any(), (GenerationContext) isNull())).thenReturn(new GenerationResult(List.of()));

            service.suggest(sessionId);

            verify(generation).generate(any(), any(), (GenerationContext) isNull());
            verify(suggestionCreation).createSuggestions(any(), eq(sessionId), eq(projectId));
        }

        @Test
        @DisplayName("should concatenate segments past the watermark in ascending order")
        void should_concatenate_segments_chronologically() {
            UUID projectId = UUID.randomUUID();
            DiscoverySession session = buildSession(projectId);
            UUID sessionId = session.getId();

            List<TranscriptSegment> ascSegments = List.of(
                    finalSegment(sessionId, 1, "FIRST."),
                    finalSegment(sessionId, 2, "SECOND."),
                    finalSegment(sessionId, 3, "THIRD.")
            );

            when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
            when(segments.findFinalBySessionIdAfter(sessionId, 0)).thenReturn(ascSegments);
            when(generation.isAvailable()).thenReturn(true);
            when(embeddingPort.isAvailable()).thenReturn(false);
            when(workspaceApi.findProjectSnapshot(any())).thenReturn(Optional.empty());
            when(generation.generate(any(), any(), any())).thenReturn(new GenerationResult(List.of()));

            service.suggest(sessionId);

            ArgumentCaptor<String> transcriptCaptor = ArgumentCaptor.forClass(String.class);
            verify(generation).generate(transcriptCaptor.capture(), any(), any());
            assertThat(transcriptCaptor.getValue()).isEqualTo("FIRST. SECOND. THIRD.");
        }
    }

    @Nested
    @DisplayName("Backlog grounding")
    class BacklogGrounding {

        private final UUID projectId = UUID.randomUUID();

        private GenerationContext contextFor(DiscoverySession session) {
            when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
            when(segments.findFinalBySessionIdAfter(session.getId(), 0)).thenReturn(
                    List.of(finalSegment(session.getId(), 1, "El cliente quiere iniciar sesión.")));
            when(generation.isAvailable()).thenReturn(true);
            when(generation.generate(any(), any(), any())).thenReturn(new GenerationResult(List.of()));

            service.suggest(session.getId());

            var captor = ArgumentCaptor.forClass(GenerationContext.class);
            verify(generation).generate(any(), any(), captor.capture());
            return captor.getValue();
        }

        private ProjectSnapshot snapshot() {
            return new ProjectSnapshot(projectId, "PayApp", null,
                    List.of(), List.of(), List.of(), List.of(), null, null, List.of(), List.of());
        }

        @Test
        @DisplayName("should merge vector-similar and recent stories into the context, nearest first")
        void should_merge_similar_and_recent_stories() {
            DiscoverySession session = DiscoverySessionBuilder.aSession().withProjectId(projectId).build();
            UserStory similar = UserStoryMother.draft().withProjectId(projectId).withTitle("Login clásico").build();
            UserStory recent = UserStoryMother.draft().withProjectId(projectId).withTitle("Recién aceptada").build();
            float[] vector = new float[]{0.1f};

            when(embeddingPort.isAvailable()).thenReturn(true);
            when(embeddingPort.embed(any())).thenReturn(vector);
            when(stories.findTopSimilar(projectId, vector, 5)).thenReturn(List.of(similar));
            when(stories.findRecentByProjectId(eq(projectId), anyInt())).thenReturn(List.of(recent, similar));
            when(workspaceApi.findRelevantContext(eq(projectId), eq(vector), anyInt()))
                    .thenReturn(Optional.of(snapshot()));

            GenerationContext context = contextFor(session);

            assertThat(context.existingStories())
                    .extracting(GenerationContext.StorySummary::title)
                    .containsExactly("Login clásico", "Recién aceptada"); // deduped by id, nearest first
            assertThat(context.existingStories())
                    .extracting(GenerationContext.StorySummary::id)
                    .containsExactly(similar.getId(), recent.getId());
        }

        @Test
        @DisplayName("should fall back to recent stories when the vector search finds nothing")
        void should_fall_back_to_recent_stories_when_vector_empty() {
            DiscoverySession session = DiscoverySessionBuilder.aSession().withProjectId(projectId).build();
            UserStory recent = UserStoryMother.draft().withProjectId(projectId).withTitle("Historia previa").build();
            float[] vector = new float[]{0.1f};

            when(embeddingPort.isAvailable()).thenReturn(true);
            when(embeddingPort.embed(any())).thenReturn(vector);
            when(stories.findTopSimilar(projectId, vector, 5)).thenReturn(List.of());
            when(stories.findRecentByProjectId(eq(projectId), anyInt())).thenReturn(List.of(recent));
            when(workspaceApi.findRelevantContext(eq(projectId), eq(vector), anyInt()))
                    .thenReturn(Optional.of(snapshot()));

            GenerationContext context = contextFor(session);

            assertThat(context.existingStories())
                    .extracting(GenerationContext.StorySummary::title)
                    .containsExactly("Historia previa");
        }

        @Test
        @DisplayName("should keep the backlog visible when the embedding call fails")
        void should_survive_embedding_failure() {
            DiscoverySession session = DiscoverySessionBuilder.aSession().withProjectId(projectId).build();
            UserStory recent = UserStoryMother.draft().withProjectId(projectId).withTitle("Historia previa").build();

            when(embeddingPort.isAvailable()).thenReturn(true);
            when(embeddingPort.embed(any())).thenThrow(new RuntimeException("provider timed out"));
            when(stories.findRecentByProjectId(eq(projectId), anyInt())).thenReturn(List.of(recent));
            when(workspaceApi.findProjectSnapshot(projectId)).thenReturn(Optional.of(snapshot()));

            GenerationContext context = contextFor(session);

            assertThat(context.existingStories())
                    .extracting(GenerationContext.StorySummary::title)
                    .containsExactly("Historia previa");
            verify(workspaceApi, never()).findRelevantContext(any(), any(), anyInt());
        }

        @Test
        @DisplayName("should list the session's pending suggestions so the model does not repeat them")
        void should_list_pending_suggestions() {
            DiscoverySession session = DiscoverySessionBuilder.aSession().withProjectId(projectId).build();

            Suggestion pendingStory = Suggestion.newStory(session.getId(), projectId,
                    "Login con 2FA", "usuario", "autenticarme con 2FA", "más seguridad", Priority.HIGH, 3);
            Suggestion pendingQuestion = Suggestion.clarifyingQuestion(session.getId(), projectId,
                    "¿Qué roles existen?");

            when(embeddingPort.isAvailable()).thenReturn(false);
            when(suggestions.findAllBySessionIdAndStatus(session.getId(), SuggestionStatus.PENDING))
                    .thenReturn(List.of(pendingStory, pendingQuestion));
            when(workspaceApi.findProjectSnapshot(projectId)).thenReturn(Optional.of(snapshot()));

            GenerationContext context = contextFor(session);

            assertThat(context.alreadySuggested())
                    .containsExactly("Login con 2FA", "¿Qué roles existen?");
        }
    }

    @Nested
    @DisplayName("Early exits")
    class EarlyExits {

        @Test
        @DisplayName("should silently return when session does not exist")
        void should_skip_when_session_not_found() {
            UUID sessionId = UUID.randomUUID();
            when(sessions.findById(sessionId)).thenReturn(Optional.empty());

            service.suggest(sessionId);

            verifyNoInteractions(segments, generation, suggestionCreation);
        }

        @Test
        @DisplayName("should skip when no new segments past the watermark")
        void should_skip_when_no_segments() {
            DiscoverySession session = buildSession(UUID.randomUUID());
            when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
            when(segments.findFinalBySessionIdAfter(session.getId(), 0)).thenReturn(List.of());

            service.suggest(session.getId());

            verifyNoInteractions(generation, suggestionCreation);
        }

        @Test
        @DisplayName("should skip when generation model is unavailable")
        void should_skip_when_generation_unavailable() {
            DiscoverySession session = buildSession(UUID.randomUUID());
            when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
            when(segments.findFinalBySessionIdAfter(session.getId(), 0)).thenReturn(
                    List.of(finalSegment(session.getId(), 1, "texto"))
            );
            when(generation.isAvailable()).thenReturn(false);

            service.suggest(session.getId());

            verify(generation, never()).generate(any(), any());
            verifyNoInteractions(suggestionCreation);
        }
    }
}
