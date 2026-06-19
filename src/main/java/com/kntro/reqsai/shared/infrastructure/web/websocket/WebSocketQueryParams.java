package com.kntro.reqsai.shared.infrastructure.web.websocket;

import org.jspecify.annotations.Nullable;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;

/** Utility for extracting typed query parameters from a {@link WebSocketSession} URI. */
public final class WebSocketQueryParams {

    private WebSocketQueryParams() {}

    /**
     * Parses a query parameter as a {@link UUID}.
     *
     * @return the parsed value, or {@code null} if the URI is absent, the param is missing, or the
     *         value is not a valid UUID
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
