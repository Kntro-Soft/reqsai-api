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
 * <p>Centralises the wiring that every raw (non-STOMP) binary WebSocket endpoint in the system
 * needs:
 * <ul>
 *   <li>{@link WebSocketJwtHandshakeInterceptor} — authenticates the JWT from {@code ?token=}
 *       and pre-resolves the tenant schema into the WS session attributes.</li>
 *   <li>{@code reqsai.websocket.allowed-origins} — shared CORS origin list (defaults to
 *       {@code http://localhost:4200} for local development).</li>
 * </ul>
 *
 * <p>Subclasses implement {@link #path()} and {@link #handler()} to register their specific
 * endpoint without repeating auth or CORS boilerplate. Because this class implements
 * {@link WebSocketConfigurer}, subclasses only need {@code @Configuration @EnableWebSocket}:
 *
 * <pre>{@code
 * @Configuration
 * @EnableWebSocket
 * class MyWebSocketConfig extends AbstractWebSocketBinaryConfig {
 *
 *     private final MyWebSocketHandler handler;
 *
 *     MyWebSocketConfig(TokenVerifier tv, TenantSchemaResolver sr, MyWebSocketHandler h) {
 *         super(tv, sr);
 *         this.handler = h;
 *     }
 *
 *     @Override protected String path()                { return "/ws/my-endpoint"; }
 *     @Override protected BinaryWebSocketHandler handler() { return handler; }
 * }
 * }</pre>
 *
 * @see WebSocketJwtHandshakeInterceptor
 * @see TenantAwareBinaryWebSocketHandler
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

    /**
     * The WebSocket endpoint path at which the handler is registered, e.g. {@code /ws/stt}.
     * Clients connect to {@code ws://<host><path>?token=<jwt>&...}.
     */
    protected abstract String path();

    /**
     * The concrete {@link BinaryWebSocketHandler} that processes audio frames and lifecycle
     * events for this endpoint.
     */
    protected abstract BinaryWebSocketHandler handler();

    @Override
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        registry.addHandler(handler(), path())
                .addInterceptors(new WebSocketJwtHandshakeInterceptor(tokenVerifier, schemaResolver))
                .setAllowedOriginPatterns(allowedOrigins.toArray(String[]::new));
    }
}
