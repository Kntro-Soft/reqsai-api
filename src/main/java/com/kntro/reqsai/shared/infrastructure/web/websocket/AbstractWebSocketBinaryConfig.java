package com.kntro.reqsai.shared.infrastructure.web.websocket;

import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantSchemaResolver;
import com.kntro.reqsai.shared.infrastructure.security.TokenVerifier;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.util.List;

/**
 * Base configuration for authenticated binary WebSocket endpoints.
 *
 * <p>Wires {@link WebSocketJwtHandshakeInterceptor} (JWT {@code ?token=} auth + tenant resolution)
 * and the shared {@code reqsai.websocket.allowed-origins} property. Subclasses provide the
 * endpoint path and the concrete handler:
 *
 * <pre>{@code
 * @Configuration
 * @EnableWebSocket
 * @RequiredArgsConstructor
 * class MyWebSocketConfig extends AbstractWebSocketBinaryConfig {
 *
 *     private final MyWebSocketHandler handler;
 *
 *     @Override protected String path()    { return "/ws/my-endpoint"; }
 *     @Override protected BinaryWebSocketHandler handler() { return handler; }
 * }
 * }</pre>
 */
public abstract class AbstractWebSocketBinaryConfig implements WebSocketConfigurer {

    private final TokenVerifier tokenVerifier;
    private final TenantSchemaResolver schemaResolver;

    @Value("${reqsai.websocket.allowed-origins:http://localhost:4200}")
    private List<String> allowedOrigins;

    protected AbstractWebSocketBinaryConfig(TokenVerifier tokenVerifier, TenantSchemaResolver schemaResolver) {
        this.tokenVerifier = tokenVerifier;
        this.schemaResolver = schemaResolver;
    }

    /** WebSocket endpoint path, e.g. {@code /ws/stt}. */
    protected abstract String path();

    /** The concrete handler for this endpoint. */
    protected abstract BinaryWebSocketHandler handler();

    @Override
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        registry.addHandler(handler(), path())
                .addInterceptors(new WebSocketJwtHandshakeInterceptor(tokenVerifier, schemaResolver))
                .setAllowedOriginPatterns(allowedOrigins.toArray(String[]::new));
    }
}
