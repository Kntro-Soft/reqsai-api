package com.kntro.reqsai.discovery.interfaces.websocket.stt;

import com.kntro.reqsai.discovery.domain.event.DiscoverySessionRecordingPausedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionRecordingStoppedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;

/**
 * Closes the live STT WebSocket channel when the discovery session leaves the {@code RECORDING}
 * state via a domain event, so the STT provider connection is released promptly rather than waiting
 * for a client-side disconnect or a network timeout.
 *
 * <ul>
 *   <li><b>Paused</b> — close with {@link CloseStatus#NORMAL} (1000); the client is expected to
 *       reconnect when the session is resumed.</li>
 *   <li><b>Stopped</b> — close with {@link CloseStatus#NORMAL} (1000); recording is finished.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
class SttSessionLifecycleListener {

    private final SttSessionRegistry registry;

    @ApplicationModuleListener
    void onPaused(DiscoverySessionRecordingPausedEvent event) {
        log.debug("Session {} paused — closing STT channel", event.sessionId());
        registry.closeIfOpen(event.sessionId(), CloseStatus.NORMAL.withReason("session paused"));
    }

    @ApplicationModuleListener
    void onStopped(DiscoverySessionRecordingStoppedEvent event) {
        log.debug("Session {} stopped — closing STT channel", event.sessionId());
        registry.closeIfOpen(event.sessionId(), CloseStatus.NORMAL.withReason("session stopped"));
    }
}
