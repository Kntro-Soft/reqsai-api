package com.kntro.reqsai.shared.application.notification;

/**
 * Port for pushing real-time notifications to clients (WebSocket/STOMP).
 * <p>
 * Application/domain code depends on this interface, never on the messaging infrastructure. The
 * default adapter is {@code StompRealtimeNotifier}. Bounded contexts publish updates (e.g. a live
 * capture session in {@code discovery}) by injecting this port.
 */
public interface RealtimeNotifier {

    /**
     * Sends a payload to a broker destination (e.g. {@code /topic/sessions/123}).
     */
    void send(String destination, Object payload);

    /**
     * Sends a payload to a single user's queue (e.g. {@code /queue/notifications} for {@code username}).
     */
    void sendToUser(String username, String destination, Object payload);

    /**
     * Broadcasts to {@code /topic/<topic>} — convenience for the common case.
     */
    default void broadcast(String topic, Object payload) {
        send("/topic/" + topic, payload);
    }
}
