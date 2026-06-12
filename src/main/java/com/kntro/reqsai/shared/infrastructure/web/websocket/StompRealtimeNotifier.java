package com.kntro.reqsai.shared.infrastructure.web.websocket;

import com.kntro.reqsai.shared.application.notification.RealtimeNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * STOMP adapter for {@link RealtimeNotifier}, backed by {@link SimpMessagingTemplate}.
 * Send failures are logged, never propagated — a dropped notification must not break the caller's
 * transaction or request.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StompRealtimeNotifier implements RealtimeNotifier {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void send(String destination, Object payload) {
        try {
            messagingTemplate.convertAndSend(destination, payload);
        } catch (Exception e) {
            log.error("Failed to send WebSocket message to {}", destination, e);
        }
    }

    @Override
    public void sendToUser(String username, String destination, Object payload) {
        try {
            messagingTemplate.convertAndSendToUser(username, destination, payload);
        } catch (Exception e) {
            log.error("Failed to send WebSocket message to user {} at {}", username, destination, e);
        }
    }
}
