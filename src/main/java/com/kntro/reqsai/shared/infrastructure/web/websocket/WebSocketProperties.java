package com.kntro.reqsai.shared.infrastructure.web.websocket;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * WebSocket/STOMP configuration, bound from {@code reqsai.websocket.*}.
 * <p>
 * <strong>Broker mode matters for scaling.</strong> The default {@code SIMPLE} (in-memory) broker only
 * knows about connections on the local JVM, so with more than one instance (e.g. several ECS Fargate
 * tasks) a broadcast won't reach clients connected to a different instance. For multi-instance
 * production, switch {@code broker.mode} to {@code RELAY} and point it at an external STOMP broker
 * (RabbitMQ/ActiveMQ — on AWS, Amazon MQ); every instance then relays through it. No code change, just
 * config. (Relay requires {@code io.projectreactor.netty:reactor-netty} on the classpath.)
 *
 * @param endpoint          STOMP handshake endpoint (default {@code /ws})
 * @param allowedOrigins    permitted origins for the handshake
 * @param topicPrefix       broker prefix for server→client broadcasts (default {@code /topic})
 * @param applicationPrefix prefix for client→server messages (default {@code /app})
 * @param userPrefix        prefix for user-specific destinations (default {@code /user})
 * @param enableSockJs      enable the SockJS fallback (default {@code false}; native WS only)
 * @param broker            broker mode + (when {@code RELAY}) external broker connection
 */
@ConfigurationProperties(prefix = "reqsai.websocket")
public record WebSocketProperties(
        String endpoint,
        List<String> allowedOrigins,
        String topicPrefix,
        String applicationPrefix,
        String userPrefix,
        boolean enableSockJs,
        Broker broker
) {
    public WebSocketProperties {
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "/ws";
        }
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            allowedOrigins = List.of("http://localhost:4200");
        }
        if (topicPrefix == null || topicPrefix.isBlank()) {
            topicPrefix = "/topic";
        }
        if (applicationPrefix == null || applicationPrefix.isBlank()) {
            applicationPrefix = "/app";
        }
        if (userPrefix == null || userPrefix.isBlank()) {
            userPrefix = "/user";
        }
        if (broker == null) {
            broker = new Broker(null, null, 0, null, null);
        }
    }

    public enum Mode {
        /** In-memory broker — single instance / dev only. */
        SIMPLE,
        /** Relay to an external STOMP broker (RabbitMQ/ActiveMQ/Amazon MQ) — multi-instance. */
        RELAY
    }

    /**
     * Broker settings. {@code host}/{@code port}/{@code login}/{@code passcode} apply only in
     * {@code RELAY} mode.
     *
     * @param mode     {@link Mode#SIMPLE} (default) or {@link Mode#RELAY}
     * @param host     external broker host (STOMP)
     * @param port     external broker STOMP port (default 61613)
     * @param login    broker username
     * @param passcode broker password
     */
    public record Broker(Mode mode, String host, int port, String login, String passcode) {
        public Broker {
            if (mode == null) {
                mode = Mode.SIMPLE;
            }
            if (host == null || host.isBlank()) {
                host = "localhost";
            }
            if (port <= 0) {
                port = 61613;
            }
            if (login == null) {
                login = "guest";
            }
            if (passcode == null) {
                passcode = "guest";
            }
        }
    }
}
