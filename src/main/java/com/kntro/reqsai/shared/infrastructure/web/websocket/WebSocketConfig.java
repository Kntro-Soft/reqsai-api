package com.kntro.reqsai.shared.infrastructure.web.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP-over-WebSocket configuration (shared infrastructure used by all bounded contexts).
 * <p>
 * Client→server messages are prefixed {@code /app}; server→client broadcasts go to {@code /topic} and
 * user destinations to {@code /user}. The handshake endpoint and origins come from
 * {@link WebSocketProperties}; {@link StompAuthChannelInterceptor} authenticates CONNECT frames via the
 * JWT {@code TokenVerifier}. The HTTP handshake path is permitted in {@code SecurityConfiguration}
 * ({@code /ws/**}); auth happens at the STOMP layer.
 * <p>
 * The broker is <strong>switchable</strong> (see {@link WebSocketProperties}): a heartbeat-enabled
 * in-memory {@code SIMPLE} broker by default, or an external {@code RELAY} (RabbitMQ/ActiveMQ/Amazon MQ)
 * for multi-instance deployments — selected by {@code reqsai.websocket.broker.mode}, no code change.
 */
@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(WebSocketProperties.class)
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketProperties properties;
    private final StompAuthChannelInterceptor authChannelInterceptor;

    @Override
    public void configureMessageBroker(@NonNull MessageBrokerRegistry registry) {
        WebSocketProperties.Broker broker = properties.broker();
        if (broker.mode() == WebSocketProperties.Mode.RELAY) {
            // External STOMP broker — required to fan out across multiple instances.
            registry.enableStompBrokerRelay(properties.topicPrefix(), properties.userPrefix())
                    .setRelayHost(broker.host())
                    .setRelayPort(broker.port())
                    .setClientLogin(broker.login())
                    .setClientPasscode(broker.passcode())
                    .setSystemLogin(broker.login())
                    .setSystemPasscode(broker.passcode());
            log.info("WebSocket broker: RELAY → {}:{}", broker.host(), broker.port());
        } else {
            // In-memory broker with heartbeats (single instance / dev).
            registry.enableSimpleBroker(properties.topicPrefix(), properties.userPrefix())
                    .setHeartbeatValue(new long[]{10_000, 10_000})
                    .setTaskScheduler(webSocketHeartbeatScheduler());
            log.info("WebSocket broker: SIMPLE (in-memory, single instance)");
        }
        registry.setApplicationDestinationPrefixes(properties.applicationPrefix());
        registry.setUserDestinationPrefix(properties.userPrefix());
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        var endpoint = registry.addEndpoint(properties.endpoint())
                .setAllowedOriginPatterns(properties.allowedOrigins().toArray(String[]::new));
        if (properties.enableSockJs()) {
            endpoint.withSockJS();
        }
        log.info("WebSocket STOMP endpoint registered at {} (sockJs={})",
                properties.endpoint(), properties.enableSockJs());
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
    }

    /** Dedicated scheduler so the in-memory broker can emit/expect STOMP heartbeats. */
    @Bean
    public TaskScheduler webSocketHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }
}
