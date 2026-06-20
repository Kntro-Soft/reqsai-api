package com.kntro.reqsai.shared.infrastructure.web.websocket;

import org.jspecify.annotations.Nullable;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;

/**
 * Utility for extracting typed query parameters from a {@link WebSocketSession} URI.
 *
 * <p>Spring automatically binds {@code @RequestParam} values for REST controllers, but WebSocket
 * handlers receive a raw {@link WebSocketSession} with no equivalent binding support. This class
 * fills that gap for the parameter types used by streaming endpoints.
 */
public final class WebSocketQueryParams {

    private WebSocketQueryParams() {}

    /**
     * Parses a named query parameter from the session URI as a {@link UUID}.
     *
     * @param ws   the active WebSocket session
     * @param name the query parameter name (e.g. {@code "session"})
     * @return the parsed {@link UUID}, or {@code null} if the URI is absent, the parameter is
     *         missing, or its value is not a valid UUID format
     */
    public static @Nullable UUID parseUUID(WebSocketSession ws, String name) {
        if (ws.getUri() == null) return null;
        String query = ws.getUri().getQuery();
        if (query == null) return null;
        String prefix = name + "=";
        for (String param : query.split("&")) {
            if (param.startsWith(prefix)) {
                try {
                    return UUID.fromString(param.substring(prefix.length()));
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
