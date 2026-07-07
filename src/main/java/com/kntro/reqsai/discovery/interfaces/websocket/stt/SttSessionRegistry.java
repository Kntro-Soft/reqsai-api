package com.kntro.reqsai.discovery.interfaces.websocket.stt;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks live WebSocket sessions by discovery session id so that domain-event listeners can
 * close or signal them when the session state changes (pause, stop).
 *
 * <p>A single discovery session may only have one active WebSocket connection at a time: if a
 * second client connects with the same sessionId, it will overwrite the previous entry after the
 * first connection is rejected at the RECORDING status check.
 */
@Component
public class SttSessionRegistry {

    private final Map<UUID, WebSocketSession> bySessionId = new ConcurrentHashMap<>();

    void register(UUID sessionId, WebSocketSession ws) {
        bySessionId.put(sessionId, ws);
    }

    @SuppressWarnings("resource") // WebSocketSession is already closed when unregister is called
    void unregister(UUID sessionId) {
        bySessionId.remove(sessionId);
    }

    /**
     * Closes the WebSocket for {@code sessionId} with {@code status} if a live connection exists.
     * No-op if no connection is registered for that session.
     */
    public void closeIfOpen(UUID sessionId, CloseStatus status) {
        WebSocketSession ws = bySessionId.remove(sessionId);
        if (ws != null && ws.isOpen()) {
            try { ws.close(status); } catch (Exception ignored) {}
        }
    }
}
