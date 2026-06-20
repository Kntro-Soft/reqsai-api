package com.kntro.reqsai.discovery.infrastructure.web.websocket;

import com.kntro.reqsai.discovery.interfaces.websocket.stt.SttStreamingWebSocketHandler;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantSchemaResolver;
import com.kntro.reqsai.shared.infrastructure.security.TokenVerifier;
import com.kntro.reqsai.shared.infrastructure.web.websocket.AbstractWebSocketBinaryConfig;
import com.kntro.reqsai.shared.infrastructure.web.websocket.WebSocketJwtHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

/**
 * Registers the live-capture STT binary WebSocket endpoint at {@code /ws/stt}.
 *
 * <p>This configuration is intentionally separate from the shared STOMP {@code WebSocketConfig}:
 * raw binary audio and the STOMP message broker use different configuration interfaces and serve
 * different purposes — both can coexist in the same application context.
 *
 * <p>Auth and CORS are handled by {@link AbstractWebSocketBinaryConfig}:
 * {@link WebSocketJwtHandshakeInterceptor} verifies the {@code ?token=<jwt>} query param and
 * resolves the tenant schema before the connection is established; allowed origins are read from
 * {@code reqsai.websocket.allowed-origins}.
 */
@Configuration
@EnableWebSocket
class SttStreamingWebSocketConfig extends AbstractWebSocketBinaryConfig {

    private final SttStreamingWebSocketHandler handler;

    SttStreamingWebSocketConfig(TokenVerifier tokenVerifier, TenantSchemaResolver schemaResolver, SttStreamingWebSocketHandler handler) {
        super(tokenVerifier, schemaResolver);
        this.handler = handler;
    }

    /** @return {@code /ws/stt} — the endpoint clients connect to for live audio capture */
    @Override
    protected String path() { return "/ws/stt"; }

    @Override
    protected BinaryWebSocketHandler handler() { return handler; }
}
