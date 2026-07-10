package com.kntro.reqsai.shared.infrastructure.web.websocket;

import com.kntro.reqsai.shared.infrastructure.security.TokenVerifier;
import com.kntro.reqsai.shared.infrastructure.security.VerifiedToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * Authenticates STOMP CONNECT frames using the same {@link TokenVerifier} as the HTTP filter.
 * <p>
 * The client sends {@code Authorization: Bearer <jwt>} as a native STOMP header on CONNECT; this
 * interceptor verifies it and binds the user {@code Principal} to the session (so per-user queues and
 * {@code @MessageMapping} security work). A CONNECT with no token is left anonymous; a CONNECT with an
 * invalid token is rejected (the verifier throws).
 * <p>
 * The verified {@code userId} and {@code orgId} are also stashed in the STOMP session attributes
 * ({@link #USER_ID_ATTRIBUTE}, {@link #ORG_ID_ATTRIBUTE}). This is deliberate, not redundant with
 * {@link StompHeaderAccessor#setUser}: empirically, the {@code Principal} set on the CONNECT frame's
 * accessor does <strong>not</strong> carry over to later frames on the same STOMP session (a later
 * SUBSCRIBE/UNSUBSCRIBE frame's {@code accessor.getUser()} is {@code null}), whereas session
 * attributes are the underlying {@code WebSocketSession}'s own attribute map and do persist across
 * every frame. Session-lifecycle listeners (e.g. discovery presence) that need the caller's identity
 * outside the CONNECT frame must read it from here, not from {@code accessor.getUser()}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    /** STOMP session-attribute key holding the authenticated user id (a {@code String}). */
    public static final String USER_ID_ATTRIBUTE = "reqsai.userId";

    /** STOMP session-attribute key holding the authenticated tenant/organization id (a {@code String}). */
    public static final String ORG_ID_ATTRIBUTE = "reqsai.orgId";

    private final TokenVerifier tokenVerifier;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String header = accessor.getFirstNativeHeader("Authorization");
            if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
                VerifiedToken token = tokenVerifier.verify(header.substring(7));
                var authentication = new UsernamePasswordAuthenticationToken(
                        token.userId(), null,
                        token.role() != null ? List.of(new SimpleGrantedAuthority(token.role())) : List.of());
                accessor.setUser(authentication);
                Map<String, Object> attributes = accessor.getSessionAttributes();
                if (attributes != null) {
                    attributes.put(USER_ID_ATTRIBUTE, token.userId());
                    if (token.orgId() != null) {
                        attributes.put(ORG_ID_ATTRIBUTE, token.orgId());
                    }
                }
                log.debug("WebSocket CONNECT authenticated for user {} (tenant {})",
                        token.userId(), token.orgId());
            }
        }
        return message;
    }
}
