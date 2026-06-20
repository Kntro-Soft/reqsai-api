package com.kntro.reqsai.shared.infrastructure.web.websocket;

import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.util.function.Supplier;

/**
 * Base class for binary WebSocket handlers that operate in a multi-tenant environment.
 *
 * <h2>Problem</h2>
 * REST requests carry tenant context via the JWT filter: the filter sets {@link TenantContext}
 * at the start of the request and clears it in a {@code finally} block. WebSocket handlers break
 * this model in two ways:
 * <ul>
 *   <li>Binary frames ({@code handleBinaryMessage}) arrive on container threads that are NOT
 *       request threads — the JWT filter never runs for them.</li>
 *   <li>STT provider callbacks (transcript events) run on the provider's own I/O thread —
 *       completely outside the servlet container.</li>
 * </ul>
 * Both types of threads see an empty {@link TenantContext}, which would cause Hibernate to open
 * a connection against the {@code public} schema instead of the tenant's schema.
 *
 * <h2>Solution</h2>
 * The handshake interceptor ({@link WebSocketJwtHandshakeInterceptor}) resolves the tenant org id
 * and schema once during the initial HTTP upgrade and stores them as WS session attributes
 * ({@link #ATTR_ORG}, {@link #ATTR_SCHEMA}). Subclasses wrap any DB-touching block in
 * {@link #runWithTenant(WebSocketSession, Runnable)}, which reads those attributes and manages
 * the {@link TenantContext} thread-local for the duration of the call.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * event -> runWithTenant(ws, () -> commandHandler.handle(...))
 * }</pre>
 *
 * @see WebSocketJwtHandshakeInterceptor
 * @see AbstractWebSocketBinaryConfig
 */
@Slf4j
public abstract class TenantAwareBinaryWebSocketHandler extends BinaryWebSocketHandler {

    /**
     * WS session attribute key for the authenticated user id.
     * Set by {@link WebSocketJwtHandshakeInterceptor} during handshake.
     */
    public static final String ATTR_USER = "ws.user";

    /**
     * WS session attribute key for the tenant (organisation) id.
     * Set by {@link WebSocketJwtHandshakeInterceptor} during handshake.
     */
    public static final String ATTR_ORG = "tenant.org";

    /**
     * WS session attribute key for the resolved PostgreSQL schema.
     * Set by {@link WebSocketJwtHandshakeInterceptor} during handshake; avoids a DB lookup per callback.
     */
    public static final String ATTR_SCHEMA = "tenant.schema";

    /**
     * Runs {@code action} under the tenant context stored in {@code ws} attributes, clearing the
     * thread-local in a {@code finally} block regardless of outcome.
     *
     * <p>Use this wrapper around any block that touches the database in an off-request context
     * (e.g. STT provider callbacks, async consumers).
     *
     * @param ws     the active WebSocket session whose attributes carry the tenant identity
     * @param action the database-touching block to run under the tenant context
     */
    protected void runWithTenant(WebSocketSession ws, Runnable action) {
        TenantContext.runWith(tenantSnapshot(ws), action);
    }

    protected TenantContext.TenantSnapshot tenantSnapshot(WebSocketSession ws) {
        String orgId = (String) ws.getAttributes().getOrDefault(ATTR_ORG, TenantContext.DEFAULT_SCHEMA);
        String schema = (String) ws.getAttributes().getOrDefault(ATTR_SCHEMA, TenantContext.DEFAULT_SCHEMA);
        return new TenantContext.TenantSnapshot(orgId, schema);
    }

    /**
     * Runs {@code supplier} under the tenant context stored in {@code ws} attributes and returns
     * its result. Use this when a DB-touching block must return a value (e.g. a status check on
     * a connection establishment).
     *
     * @param ws       the active WebSocket session whose attributes carry the tenant identity
     * @param supplier the block to run under the tenant context
     * @param <T>      return type
     */
    protected <T> T runWithTenantAndReturn(WebSocketSession ws, Supplier<T> supplier) {
        TenantContext.TenantSnapshot t = tenantSnapshot(ws);
        TenantContext.setCurrentTenant(t.tenantId());
        TenantContext.setCurrentSchema(t.tenantSchema());
        try {
            return supplier.get();
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Closes {@code ws} with the given status, suppressing any I/O exception that may occur when
     * the connection is already gone (e.g. a client disconnected before the close frame was sent).
     *
     * @param ws     the session to close
     * @param status the close status to send to the client
     */
    protected void close(WebSocketSession ws, CloseStatus status) {
        try {
            ws.close(status);
        } catch (Exception e) {
            log.debug("Failed to close WS session {}: {}", ws.getId(), e.getMessage());
        }
    }
}
