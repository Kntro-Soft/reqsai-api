package com.kntro.reqsai.shared.infrastructure.web.websocket;

import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantSchemaResolver;
import com.kntro.reqsai.shared.infrastructure.security.TokenVerifier;
import com.kntro.reqsai.shared.infrastructure.security.VerifiedToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Authenticates binary WebSocket handshakes via a JWT passed as the {@code ?token=<jwt>} query
 * parameter, and pre-resolves the tenant schema so handlers avoid a DB lookup per callback.
 *
 * <h2>Why query param instead of Authorization header?</h2>
 * The browser {@code WebSocket} API does not support custom headers during the HTTP upgrade
 * request. Passing the JWT as a query parameter is the standard workaround for raw WebSocket
 * connections. The STOMP channel uses its own {@code StompAuthChannelInterceptor} and is not
 * covered by this interceptor.
 *
 * <h2>What it stores</h2>
 * On a successful handshake, three attributes are written to the WS session so the handler can
 * read them later without hitting the database again:
 * <ul>
 *   <li>{@link TenantAwareBinaryWebSocketHandler#ATTR_USER} — authenticated user id</li>
 *   <li>{@link TenantAwareBinaryWebSocketHandler#ATTR_ORG} — tenant (organisation) id</li>
 *   <li>{@link TenantAwareBinaryWebSocketHandler#ATTR_SCHEMA} — resolved PostgreSQL schema</li>
 * </ul>
 * A missing or invalid token causes the handshake to be rejected (returns {@code false}), which
 * Spring translates to an HTTP 403 response before the WebSocket upgrade completes.
 *
 * @see TenantAwareBinaryWebSocketHandler
 * @see AbstractWebSocketBinaryConfig
 */
@RequiredArgsConstructor
@Slf4j
public class WebSocketJwtHandshakeInterceptor implements HandshakeInterceptor {

    private final TokenVerifier tokenVerifier;
    private final TenantSchemaResolver schemaResolver;

    /**
     * Verifies the JWT from {@code ?token=} and populates WS session attributes on success.
     *
     * @return {@code true} to allow the upgrade; {@code false} to reject it with HTTP 403
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, @NonNull ServerHttpResponse response,
                                   @NonNull WebSocketHandler wsHandler, @NonNull Map<String, Object> attributes) {
        String token = extractToken(request.getURI().getQuery());
        if (token == null) {
            log.warn("WS handshake rejected — no token query param (path: {})", request.getURI().getPath());
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        try {
            VerifiedToken verified = tokenVerifier.verify(token);
            attributes.put(TenantAwareBinaryWebSocketHandler.ATTR_USER, verified.userId());
            if (verified.orgId() != null) {
                String schema = schemaResolver.resolveTenantSchema(verified.orgId());
                if (TenantContext.DEFAULT_SCHEMA.equals(schema)) {
                    log.warn("WS handshake rejected — tenant {} not provisioned (path: {})", verified.orgId(), request.getURI().getPath());
                    response.setStatusCode(HttpStatus.FORBIDDEN);
                    return false;
                }
                attributes.put(TenantAwareBinaryWebSocketHandler.ATTR_ORG, verified.orgId());
                attributes.put(TenantAwareBinaryWebSocketHandler.ATTR_SCHEMA, schema);
            }
            return true;
        } catch (Exception e) {
            log.warn("WS handshake rejected — invalid token (path: {}): {}",
                    request.getURI().getPath(), e.getMessage());
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
    }

    /** No post-handshake action required. */
    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                               @NonNull WebSocketHandler wsHandler, Exception exception) {
    }

    /**
     * Extracts the raw JWT value from the {@code token=} query parameter.
     *
     * @param query the raw query string (maybe {@code null})
     * @return the token value, or {@code null} if the param is absent
     */
    private @Nullable String extractToken(@Nullable String query) {
        if (query == null) return null;
        for (String param : query.split("&")) {
            if (param.startsWith("token=")) {
                return param.substring("token=".length());
            }
        }
        return null;
    }
}
