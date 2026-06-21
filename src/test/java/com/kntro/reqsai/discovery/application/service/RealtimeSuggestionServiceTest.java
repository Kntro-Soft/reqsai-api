package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.*;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import com.kntro.reqsai.discovery.mothers.DiscoverySessionBuilder;
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

    @InjectMocks
    private RealtimeSuggestionService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "contextWindow", 10);
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
            when(segments.findRecentFinalBySessionId(sessionId, 10)).thenReturn(
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
            when(segments.findRecentFinalBySessionId(sessionId, 10)).thenReturn(
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
            when(segments.findRecentFinalBySessionId(sessionId, 10)).thenReturn(
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
        @DisplayName("should concatenate segments in chronological order")
        void should_concatenate_segments_chronologically() {
            UUID projectId = UUID.randomUUID();
            DiscoverySession session = buildSession(projectId);
            UUID sessionId = session.getId();

            // JPA returns DESC, service reverses to chronological
            List<TranscriptSegment> descSegments = List.of(
                    finalSegment(sessionId, 3, "THIRD."),
                    finalSegment(sessionId, 2, "SECOND."),
                    finalSegment(sessionId, 1, "FIRST.")
            );

            when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
            when(segments.findRecentFinalBySessionId(sessionId, 10)).thenReturn(descSegments);
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
        @DisplayName("should skip when no final segments exist yet")
        void should_skip_when_no_segments() {
            DiscoverySession session = buildSession(UUID.randomUUID());
            when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
            when(segments.findRecentFinalBySessionId(session.getId(), 10)).thenReturn(List.of());

            service.suggest(session.getId());

            verifyNoInteractions(generation, suggestionCreation);
        }

        @Test
        @DisplayName("should skip when generation model is unavailable")
        void should_skip_when_generation_unavailable() {
            DiscoverySession session = buildSession(UUID.randomUUID());
            when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
            when(segments.findRecentFinalBySessionId(session.getId(), 10)).thenReturn(
                    List.of(finalSegment(session.getId(), 1, "texto"))
            );
            when(generation.isAvailable()).thenReturn(false);

            service.suggest(session.getId());

            verify(generation, never()).generate(any(), any());
            verifyNoInteractions(suggestionCreation);
        }
    }
}
