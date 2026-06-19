package com.kntro.reqsai.shared.infrastructure.web.websocket;

import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

/**
 * Base class for binary WebSocket handlers that run in a multi-tenant context.
 *
 * <p>Transcription callbacks and other off-request tasks run on threads that never pass through the
 * JWT filter, so they carry no tenant context. Subclasses call {@link #runWithTenant} to set and
 * clear {@link TenantContext} around any block that touches the database, using the {@code orgId}
 * and {@code schema} stored as WS session attributes during the authenticated handshake.
 *
 * <p>The handshake interceptor is responsible for populating {@link #ATTR_ORG} and
 * {@link #ATTR_SCHEMA} before the connection is established.
 */
@Slf4j
public abstract class TenantAwareBinaryWebSocketHandler extends BinaryWebSocketHandler {

    /** WS session attribute key for the authenticated user id. Set by the handshake interceptor. */
    public static final String ATTR_USER = "ws.user";

    /** WS session attribute key for the tenant (organisation) id. Set by the handshake interceptor. */
    public static final String ATTR_ORG = "tenant.org";

    /** WS session attribute key for the resolved PostgreSQL schema. Set by the handshake interceptor. */
    public static final String ATTR_SCHEMA = "tenant.schema";

    /**
     * Runs {@code action} under the tenant context stored in {@code ws} attributes,
     * clearing the thread-local in a {@code finally} block.
     */
    protected void runWithTenant(WebSocketSession ws, Runnable action) {
        String orgId = (String) ws.getAttributes().get(ATTR_ORG);
        String schema = (String) ws.getAttributes().getOrDefault(ATTR_SCHEMA, TenantContext.DEFAULT_SCHEMA);
        TenantContext.runWith(orgId, schema, action);
    }

    /**
     * Closes {@code ws} with the given status, suppressing any I/O exception that may occur if the
     * connection is already gone.
     */
    protected void close(WebSocketSession ws, CloseStatus status) {
        try {
            ws.close(status);
        } catch (Exception e) {
            log.debug("Failed to close WS session {}: {}", ws.getId(), e.getMessage());
        }
    }
}
