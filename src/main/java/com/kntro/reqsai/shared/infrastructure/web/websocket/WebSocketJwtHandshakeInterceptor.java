package com.kntro.reqsai.shared.infrastructure.web.websocket;

import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantSchemaResolver;
import com.kntro.reqsai.shared.infrastructure.security.TokenVerifier;
import com.kntro.reqsai.shared.infrastructure.security.VerifiedToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Authenticates a WebSocket handshake via the JWT passed as {@code ?token=<jwt>} query param
 * (used for raw binary sockets where the STOMP channel interceptor does not apply).
 *
 * <p>On success, stores the user id, tenant (org) id, and resolved PostgreSQL schema in the WS
 * session attributes so handlers can set the tenant context for off-request callbacks without
 * hitting the DB per event. Attribute keys are defined in {@link TenantAwareBinaryWebSocketHandler}.
 */
@RequiredArgsConstructor
@Slf4j
public class WebSocketJwtHandshakeInterceptor implements HandshakeInterceptor {

    private final TokenVerifier tokenVerifier;
    private final TenantSchemaResolver schemaResolver;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, @NonNull ServerHttpResponse response,
                                   @NonNull WebSocketHandler wsHandler, @NonNull Map<String, Object> attributes) {
        String token = extractToken(request.getURI().getQuery());
        if (token == null) {
            log.warn("WS handshake rejected — no token query param (path: {})", request.getURI().getPath());
            return false;
        }
        try {
            VerifiedToken verified = tokenVerifier.verify(token);
            attributes.put(TenantAwareBinaryWebSocketHandler.ATTR_USER, verified.userId());
            if (verified.orgId() != null) {
                attributes.put(TenantAwareBinaryWebSocketHandler.ATTR_ORG, verified.orgId());
                attributes.put(TenantAwareBinaryWebSocketHandler.ATTR_SCHEMA,
                        schemaResolver.resolveTenantSchema(verified.orgId()));
            }
            return true;
        } catch (Exception e) {
            log.warn("WS handshake rejected — invalid token (path: {}): {}",
                    request.getURI().getPath(), e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                               @NonNull WebSocketHandler wsHandler, Exception exception) {
    }

    private String extractToken(String query) {
        if (query == null) return null;
        for (String param : query.split("&")) {
            if (param.startsWith("token=")) {
                return param.substring("token=".length());
            }
        }
        return null;
    }
}
