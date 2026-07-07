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

/**
 * Authenticates STOMP CONNECT frames using the same {@link TokenVerifier} as the HTTP filter.
 * <p>
 * The client sends {@code Authorization: Bearer <jwt>} as a native STOMP header on CONNECT; this
 * interceptor verifies it and binds the user {@code Principal} to the session (so per-user queues and
 * {@code @MessageMapping} security work). A CONNECT with no token is left anonymous; a CONNECT with an
 * invalid token is rejected (the verifier throws).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StompAuthChannelInterceptor implements ChannelInterceptor {

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
                log.debug("WebSocket CONNECT authenticated for user {} (tenant {})",
                        token.userId(), token.orgId());
            }
        }
        return message;
    }
}
