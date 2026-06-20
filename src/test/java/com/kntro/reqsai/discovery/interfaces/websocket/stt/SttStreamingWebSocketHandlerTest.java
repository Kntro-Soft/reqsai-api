package com.kntro.reqsai.discovery.interfaces.websocket.stt;

import com.kntro.reqsai.discovery.application.command.AppendTranscriptSegmentCommand;
import com.kntro.reqsai.discovery.application.handler.AppendTranscriptSegmentCommandHandler;
import com.kntro.reqsai.discovery.application.handler.StartSttStreamCommandHandler;
import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.StreamingTranscriptionPort;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.SessionStatus;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import com.kntro.reqsai.shared.infrastructure.web.websocket.TenantAwareBinaryWebSocketHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SttStreamingWebSocketHandler}. Uses a controllable
 * {@link FakeStreaming} port and the real {@link StartSttStreamCommandHandler} wired with a
 * mocked {@link DiscoverySessionRepository}, so both the WS adapter and the application-layer
 * RECORDING guard are exercised together.
 */
@DisplayName("Interfaces: SttStreamingWebSocketHandler")
class SttStreamingWebSocketHandlerTest {

    private final FakeStreaming streaming = new FakeStreaming();
    private final DiscoverySessionRepository sessions = mock(DiscoverySessionRepository.class);
    private final StartSttStreamCommandHandler startStream =
            new StartSttStreamCommandHandler(sessions, streaming);
    private final AppendTranscriptSegmentCommandHandler appendHandler =
            mock(AppendTranscriptSegmentCommandHandler.class);
    private final SttSessionRegistry registry = new SttSessionRegistry();
    private final SttStreamingWebSocketHandler handler =
            new SttStreamingWebSocketHandler(startStream, appendHandler, registry);

    private final UUID sessionId = UUID.randomUUID();

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("should open recognizer, forward audio frame, and dispatch AppendTranscriptSegmentCommand")
    void should_stream_audio_to_segments() {
        stubRecording(sessionId);
        WebSocketSession ws = wsSession("ws-1", "session=" + sessionId, "org-1", "tenant_acme");

        handler.afterConnectionEstablished(ws);
        handler.handleBinaryMessage(ws, binaryFrame("audio"));

        assertThat(streaming.frames).hasSize(1);
        ArgumentCaptor<AppendTranscriptSegmentCommand> captor =
                ArgumentCaptor.forClass(AppendTranscriptSegmentCommand.class);
        verify(appendHandler).handle(captor.capture());
        AppendTranscriptSegmentCommand cmd = captor.getValue();
        assertThat(cmd.sessionId()).isEqualTo(sessionId);
        assertThat(cmd.text()).isEqualTo("hola");
        assertThat(cmd.isFinal()).isTrue();
        assertThat(TenantContext.getCurrentSchema()).isNull();
    }

    @Test
    @DisplayName("should dispatch partial transcripts (isFinal=false) as well as finals")
    void should_dispatch_partial_transcripts() {
        stubRecording(sessionId);
        streaming.emitFinal = false;
        WebSocketSession ws = wsSession("ws-2", "session=" + sessionId, "org-1", "tenant_acme");

        handler.afterConnectionEstablished(ws);
        handler.handleBinaryMessage(ws, binaryFrame("audio"));

        ArgumentCaptor<AppendTranscriptSegmentCommand> captor =
                ArgumentCaptor.forClass(AppendTranscriptSegmentCommand.class);
        verify(appendHandler).handle(captor.capture());
        assertThat(captor.getValue().isFinal()).isFalse();
    }

    @Test
    @DisplayName("should close the recognizer when the WebSocket connection closes")
    void should_close_recognizer() {
        stubRecording(sessionId);
        WebSocketSession ws = wsSession("ws-3", "session=" + sessionId, "org-1", "tenant_acme");
        handler.afterConnectionEstablished(ws);

        handler.afterConnectionClosed(ws, CloseStatus.NORMAL);

        assertThat(streaming.closed).isTrue();
    }

    @Test
    @DisplayName("should reject connection and close immediately when 'session' query param is absent or invalid")
    void should_reject_missing_session() throws Exception {
        WebSocketSession ws = wsSession("ws-4", "foo=bar", "org-1", "tenant_acme");

        handler.afterConnectionEstablished(ws);

        verify(ws).close(any(CloseStatus.class));
        assertThat(streaming.openCount).isZero();
    }

    @Test
    @DisplayName("should reject connection when session is not in RECORDING status")
    void should_reject_non_recording_session() throws Exception {
        DiscoverySession session = mock(DiscoverySession.class);
        when(session.getStatus()).thenReturn(SessionStatus.DRAFT);
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
        WebSocketSession ws = wsSession("ws-5", "session=" + sessionId, "org-1", "tenant_acme");

        handler.afterConnectionEstablished(ws);

        verify(ws).close(any(CloseStatus.class));
        assertThat(streaming.openCount).isZero();
    }

    @Test
    @DisplayName("should close WS via registry when lifecycle listener signals stop")
    void should_close_ws_on_lifecycle_stop() throws Exception {
        stubRecording(sessionId);
        WebSocketSession ws = wsSession("ws-6", "session=" + sessionId, "org-1", "tenant_acme");
        when(ws.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(ws);

        registry.closeIfOpen(sessionId, CloseStatus.NORMAL);

        verify(ws).close(CloseStatus.NORMAL);
    }

    // ----- helpers -----

    private void stubRecording(UUID sessionId) {
        DiscoverySession session = mock(DiscoverySession.class);
        when(session.getStatus()).thenReturn(SessionStatus.RECORDING);
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
    }

    private WebSocketSession wsSession(String id, String query, String orgId, String schema) {
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.getId()).thenReturn(id);
        when(ws.getUri()).thenReturn(URI.create("ws://localhost/ws/stt?" + query));
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(TenantAwareBinaryWebSocketHandler.ATTR_ORG, orgId);
        attrs.put(TenantAwareBinaryWebSocketHandler.ATTR_SCHEMA, schema);
        when(ws.getAttributes()).thenReturn(attrs);
        return ws;
    }

    private static BinaryMessage binaryFrame(String text) {
        return new BinaryMessage(text.getBytes(StandardCharsets.UTF_8));
    }

    /** Fake STT port: records forwarded frames and emits one transcript event per frame. */
    private static final class FakeStreaming implements StreamingTranscriptionPort {
        final List<byte[]> frames = new ArrayList<>();
        boolean closed = false;
        boolean emitFinal = true;
        int openCount = 0;

        @Override
        public Session open(Context context, Listener listener) {
            openCount++;
            return new Session() {
                @Override
                public void sendAudio(byte[] frame) {
                    frames.add(frame);
                    listener.onTranscript(new TranscriptEvent("hola", null, 0, 100, emitFinal));
                }

                @Override
                public void close() {
                    closed = true;
                }
            };
        }
    }
}
