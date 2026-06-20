package com.kntro.reqsai.discovery.interfaces.websocket.stt;

import com.kntro.reqsai.discovery.application.command.AppendTranscriptSegmentCommand;
import com.kntro.reqsai.discovery.application.command.StartSttStreamCommand;
import com.kntro.reqsai.discovery.application.handler.AppendTranscriptSegmentCommandHandler;
import com.kntro.reqsai.discovery.application.handler.StartSttStreamCommandHandler;
import com.kntro.reqsai.discovery.application.port.StreamingTranscriptionPort;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.shared.infrastructure.web.websocket.TenantAwareBinaryWebSocketHandler;
import com.kntro.reqsai.shared.infrastructure.web.websocket.WebSocketJwtHandshakeInterceptor;
import com.kntro.reqsai.shared.infrastructure.web.websocket.WebSocketQueryParams;
import com.kntro.reqsai.shared.interfaces.websocket.WebSocketController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Inbound WebSocket adapter for live-streaming audio capture ({@code /ws/stt}).
 *
 * <h2>Connection lifecycle</h2>
 * <ol>
 *   <li><b>Handshake</b> — {@link WebSocketJwtHandshakeInterceptor} authenticates the JWT and
 *       stores the tenant org id and resolved schema as session attributes.</li>
 *   <li><b>Open</b> — {@link #afterConnectionEstablished} resolves the discovery session id from
 *       {@code ?session=<uuid>} and delegates to {@link StartSttStreamCommandHandler}, which
 *       validates {@code RECORDING} status and opens the upstream STT connection.</li>
 *   <li><b>Audio-in</b> — {@link #handleBinaryMessage} forwards each raw binary frame to the
 *       provider; no domain logic runs here.</li>
 *   <li><b>Transcript-out</b> — the STT provider calls back on its own I/O thread;
 *       {@link #onTranscript} runs inside {@link #runWithTenant} so Hibernate uses the correct
 *       tenant schema. {@link AppendTranscriptSegmentCommandHandler} persists the segment and
 *       raises {@code TranscriptSegmentAppendedEvent}; after commit the realtime notification
 *       listener pushes it to {@code /topic/sessions/{id}} via STOMP.</li>
 *   <li><b>Close</b> — {@link #afterConnectionClosed} closes the provider session and unregisters
 *       from {@link SttSessionRegistry}. The lifecycle listener closes the channel when the
 *       session is paused or stopped via a domain event.</li>
 * </ol>
 *
 * <h2>Concurrency</h2>
 * Transcript callbacks run on the STT provider's I/O thread — outside the servlet container —
 * hence the {@link #runWithTenant} wrapper. {@code recognizers} and {@code wsToSession} are
 * {@link ConcurrentHashMap}s because Spring may dispatch lifecycle events on different threads.
 */
@WebSocketController
@RequiredArgsConstructor
@Slf4j
public class SttStreamingWebSocketHandler extends TenantAwareBinaryWebSocketHandler {

    private final StartSttStreamCommandHandler startStream;
    private final AppendTranscriptSegmentCommandHandler appendHandler;
    private final SttSessionRegistry registry;

    /** One provider session per WS connection, keyed by Spring WS session id. */
    private final Map<String, StreamingTranscriptionPort.Session> recognizers = new ConcurrentHashMap<>();
    /** Reverse map to unregister from {@link SttSessionRegistry} on close. */
    private final Map<String, UUID> wsToSession = new ConcurrentHashMap<>();

    /**
     * Opens the upstream STT session via {@link StartSttStreamCommandHandler}.
     *
     * <p>Rejects with {@link CloseStatus#BAD_DATA} (1007) if the {@code session} query param is
     * absent or malformed; with {@link CloseStatus#POLICY_VIOLATION} (1008) if the session is not
     * in {@code RECORDING} status or does not exist.
     */
    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession ws) {
        UUID sessionId = WebSocketQueryParams.parseUUID(ws, "session");
        if (sessionId == null) {
            close(ws, CloseStatus.BAD_DATA.withReason("missing or invalid 'session' query param"));
            return;
        }
        try {
            StreamingTranscriptionPort.Session recognizer = runWithTenantAndReturn(ws, () ->
                    startStream.handle(new StartSttStreamCommand(sessionId), event -> runWithTenant(ws, () -> onTranscript(sessionId, event))));
            recognizers.put(ws.getId(), recognizer);
            wsToSession.put(ws.getId(), sessionId);
            registry.register(sessionId, ws);
            log.debug("STT stream opened for session {} (ws {})", sessionId, ws.getId());
        } catch (DomainException e) {
            close(ws, CloseStatus.POLICY_VIOLATION.withReason(e.getMessage()));
        }
    }

    /** Forwards each raw audio frame to the STT provider without buffering or re-encoding. */
    @Override
    protected void handleBinaryMessage(@NonNull WebSocketSession ws, @NonNull BinaryMessage message) {
        StreamingTranscriptionPort.Session recognizer = recognizers.get(ws.getId());
        if (recognizer == null) return;
        ByteBuffer payload = message.getPayload();
        byte[] frame = new byte[payload.remaining()];
        payload.get(frame);
        recognizer.sendAudio(frame);
    }

    /** Closes the provider session and cleans up maps when the WebSocket connection ends. */
    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession ws, @NonNull CloseStatus status) {
        StreamingTranscriptionPort.Session recognizer = recognizers.remove(ws.getId());
        if (recognizer != null) recognizer.close();
        UUID sessionId = wsToSession.remove(ws.getId());
        if (sessionId != null) registry.unregister(sessionId);
        log.debug("STT stream closed (ws {}, {})", ws.getId(), status);
    }

    private void onTranscript(UUID sessionId, StreamingTranscriptionPort.TranscriptEvent event) {
        try {
            appendHandler.handle(new AppendTranscriptSegmentCommand(sessionId, event.text(), event.speaker(), event.startMs(), event.endMs(), event.isFinal()));
        } catch (Exception e) {
            log.warn("Dropping transcript segment for session {} — {}", sessionId, e.getMessage());
        }
    }
}
